package guardians.controllers;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import guardians.model.dtos.scheduler.CalendarSchedulerDTO;
import guardians.model.dtos.scheduler.DoctorSchedulerDTO;
import guardians.model.dtos.scheduler.ScheduleSchedulerDTO;
import guardians.model.dtos.scheduler.ShiftConfigurationSchedulerDTO;
import guardians.model.entities.Calendar;
import guardians.model.entities.Doctor;
import guardians.model.entities.Schedule;
import guardians.model.entities.ShiftConfiguration;
import guardians.model.entities.Doctor.DoctorStatus;
import guardians.model.entities.Schedule.ScheduleStatus;
import guardians.model.repositories.DoctorRepository;
import guardians.model.repositories.ScheduleRepository;
import guardians.model.repositories.ShiftConfigurationRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * This class is responsible for dealing with the communication with the
 * Scheduler system, whenever a {@link Schedule} is to be generated
 * 
 * @author miggoncan
 */
@Slf4j
@Component
public class SchedulerHandler {
	@Autowired
	private ScheduleRepository scheduleRepository;
	@Autowired
	private ShiftConfigurationRepository shiftConfRepository;
	@Autowired
	private DoctorRepository doctorRepository;

	@Value("${scheduler.command}")
	private String schedulerCommand;
	@Value("${scheduler.entryPoint}")
	private String schedulerEntryPoint;
	@Value("${scheduler.file.doctors}")
	private String doctorsFilePath;
	@Value("${scheduler.file.shiftConfs}")
	private String shiftConfsFilePath;
	@Value("${scheduler.file.calendar}")
	private String calendarFilePath;
	@Value("${scheduler.file.schedule}")
	private String scheduleFilePath;
	@Value("${scheduler.timeout}")
	private int schedulerTimeout;

	/**
	 * This method will request the scheduler to generate a {@link Schedule} for the
	 * given {@link Calendar}
	 * 
	 * @param calendar The calendar whose {@link Schedule} is to be generated
	 */
	@Async
	@Transactional
	public void startScheduleGeneration(Calendar calendar) {
		log.info("Request to start the schedule generation");

		// TODO what if this method is called while another schedule is being generated?

		log.debug("Retrieving doctors and shift configurations from the datasource");
		List<Doctor> doctors = doctorRepository.findAll();
		List<ShiftConfiguration> shiftConfs = shiftConfRepository.findAll();

		log.debug("Mapping the doctors, shift configurations and the calendar to their corresponding DTOs");
		List<DoctorSchedulerDTO> doctorDTOs = doctors.stream()
				// Ignore deleted doctors
				.filter(doctor -> doctor.getStatus() != DoctorStatus.DELETED)
				.map(doctor -> new DoctorSchedulerDTO(doctor))
				.collect(Collectors.toCollection(() -> new LinkedList<>()));
		List<ShiftConfigurationSchedulerDTO> shiftConfDTOs = shiftConfs.stream()
				// Only include the shift configuration of doctors in doctorDTOs
				.filter(shiftConf -> doctorDTOs.stream()
						.anyMatch(doctorDTO -> doctorDTO.getId().equals(shiftConf.getDoctorId())))
				.map(shiftConf -> new ShiftConfigurationSchedulerDTO(shiftConf))
				.collect(Collectors.toCollection(() -> new LinkedList<>()));
		CalendarSchedulerDTO calendarDTO = new CalendarSchedulerDTO(calendar);

		boolean errorOcurred = false;

		File doctorsFile = new File(doctorsFilePath);
		File shiftConfsFile = new File(shiftConfsFilePath);
		File calendarFile = new File(calendarFilePath);
		File scheduleFile = new File(scheduleFilePath);

		log.debug("Writing the information needed by the scheduler to files");
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			objectMapper.writeValue(doctorsFile, doctorDTOs);
			objectMapper.writeValue(shiftConfsFile, shiftConfDTOs);
			objectMapper.writeValue(calendarFile, calendarDTO);
		} catch (IOException e) {
			log.error("An error ocurred when trying to serialize the DTOs and write the to files: " + e.getMessage());
			errorOcurred = true;
		}

		Process schedulerProcess = null;
		if (!errorOcurred) {
			log.debug("Starting the scheduler process");
			try {
				schedulerProcess = new ProcessBuilder(schedulerCommand, schedulerEntryPoint, doctorsFilePath,
						shiftConfsFilePath, calendarFilePath, scheduleFilePath).start();
			} catch (IOException e) {
				log.error("An error ocurred when trying to start the scheduler process: " + e.getMessage());
				errorOcurred = true;
			}
		}

		boolean schedulerFinishedCorrectly = false;

		if (!errorOcurred) {
			log.debug("Waiting for the scheduler to finish");
			try {
				schedulerFinishedCorrectly = schedulerProcess.waitFor(schedulerTimeout, TimeUnit.MINUTES);
			} catch (InterruptedException e) {
				log.error("The schedule generator thread has been interrupted");
				errorOcurred = true;
			}
		}

		if (!errorOcurred) {
			if (!schedulerFinishedCorrectly) {
				log.error("The scheduler process is taking too long to finish. Ending the process");
				schedulerProcess.destroy();
				errorOcurred = true;
			} else {
				try {
					log.info("The scheduler finished correctly. Attempting to read the output file");
					ObjectMapper objectMapper = new ObjectMapper();
					ScheduleSchedulerDTO scheduleDTO = objectMapper.readValue(scheduleFile, ScheduleSchedulerDTO.class);
					log.debug("The generated scheduleDTO is: " + scheduleDTO);
					if (scheduleDTO == null) {
						log.info("The created schedule is null. An error should have occurred");
						errorOcurred = true;
					} else {
						log.info("Schedule generated correctly. Attempting to persist it");
						// FIXME Check the generated schedule meets the specified requirements
						Schedule generatedSchedule = scheduleDTO.toSchedule();
						generatedSchedule.setCalendar(calendar);
						Schedule savedSchedule = scheduleRepository.save(generatedSchedule);
						log.info("The schedule has been persisted");
						log.debug("The persisted schedule is: " + savedSchedule);
					}
				} catch (IOException e) {
					log.error("Could not read the generated schedule file: " + e.getMessage());
					errorOcurred = true;
				}
			}
		}

		if (errorOcurred) {
			log.info("As the schedule could not be generated, persisting a schedule with status GENERATION_ERROR");
			Schedule schedule = new Schedule(ScheduleStatus.GENERATION_ERROR);
			schedule.setCalendar(calendar);
			scheduleRepository.save(schedule);
		}

		
		log.debug("Cleanning up temporary files");
		if (doctorsFile.exists()) {
			doctorsFile.delete();
		}
		if (shiftConfsFile.exists()) {
			shiftConfsFile.delete();
		}
		if (calendarFile.exists()) {
			calendarFile.delete();
		}
		if (scheduleFile.exists()) {
			scheduleFile.delete();
		}
	}
}