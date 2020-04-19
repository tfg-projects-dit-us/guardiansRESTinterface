package es.us.alumn.miggoncan2.model.entities;

import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.hibernate.validator.constraints.Range;

import com.fasterxml.jackson.annotation.JsonBackReference;

import es.us.alumn.miggoncan2.model.entities.primarykeys.ScheduleDayPK;
import lombok.Data;

//TODO test this day is valid this month (e.g. 30-February)

@Data
@Entity
@IdClass(ScheduleDayPK.class)
public class ScheduleDay {
	@Id
	@Range(min = 1, max = 31)
	private Integer day;
	@Id
	@Range(min = 1, max = 12)
	@Column(name = "schedule_calendar_month")
	private Integer month;
	@Id
	@Range(min = 1970)
	@Column(name = "schedule_calendar_year")
	private Integer year;
	@MapsId
	@ManyToOne
	@JsonBackReference
	private Schedule schedule;
	
	@NotNull
	private Boolean isWorkingDay;
	
	@ManyToMany
	@NotEmpty
	private Set<Doctor> cycle;
	
	@ManyToMany
	@NotEmpty
	private Set<Doctor> shifts;
	
	@ManyToMany
	private Set<Doctor> consultations;
	
	
	public ScheduleDay(Boolean isWorkingDay) {
		this.isWorkingDay = isWorkingDay;
	}
	
	public ScheduleDay() {}
	
	@Override
	public String toString() {
		return ScheduleDay.class.getSimpleName()
				+ "("
					+ "day=" + day + ", "
					+ "month=" + month + ", "
					+ "year=" + year + ", "
					+ "isWorkingDay=" + isWorkingDay + ", "
					+ "cycle=" + cycle + ", "
					+ "shifts=" + shifts + ", "
					+ "consultations=" + consultations
				+ ")";
	}
	
	public void setSchedule(Schedule schedule) {
		this.schedule = schedule;
		if (schedule != null) {
			this.month = schedule.getMonth();
			this.year = schedule.getYear();
		}
	}
}
