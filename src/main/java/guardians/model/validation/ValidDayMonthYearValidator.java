package es.us.alumn.miggoncan2.model.validation;

import java.time.DateTimeException;
import java.time.LocalDate;

import lombok.extern.slf4j.Slf4j;

/**
 * This class will make sure that, provided a day, month and year, they are
 * valid. This is, for example, the 29th of February should be an invalid date
 * 
 * @author miggoncan
 */
@Slf4j
public class ValidDayMonthYearValidator {
	public boolean isValid(Integer day, Integer month, Integer year) {
		log.info("Request to validate the date: " + day + "/" + month + "/" + year);
		boolean valid = false;
		try {
			LocalDate.of(year, month, day);
			log.info("The given date is valid");
			valid = true;
		} catch (DateTimeException e) {
			log.info("The given date is invalid");
		}
		return valid;
	}
}