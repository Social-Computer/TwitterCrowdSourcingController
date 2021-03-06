package recoin.mongodb_version.rest;

import spark.Request;

/**
 * 
 * @author user Saud Aljaloud
 * @author email sza1g10@ecs.soton.ac.uk
 *
 */

public class RestMethods {


	public static Integer setOffset(Request request) {
		Integer offset = 0;
		String offsetString = request.queryParams("offset");
		if (offsetString != null) {
			offset = Integer.valueOf(offsetString);
		}
		return offset;
	}

	public static Integer setLimit(Request request) {
		Integer limit = 200;
		String limitString = request.queryParams("limit");
		if (limitString != null) {
			limit = Integer.valueOf(limitString);
		}
		return limit;
	}
}
