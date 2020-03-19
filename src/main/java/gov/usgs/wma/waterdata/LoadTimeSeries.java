package gov.usgs.wma.waterdata;

import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LoadTimeSeries implements Function<RequestObject, ResultObject> {

	private static final Logger LOG = LoggerFactory.getLogger(LoadTimeSeries.class);

	public static final String STATUS_SUCCESS = "success";
	public static final String STATUS_FAIL = "fail";

	private TransformDao transformDao;
	private ObservationDao observationDao;

	@Autowired
	public LoadTimeSeries(TransformDao transformDao, ObservationDao observationDao) {
		this.transformDao = transformDao;
		this.observationDao = observationDao;
	}

	@Override
	public  ResultObject apply(RequestObject request) {
		return processRequest(request);
	}

	protected ResultObject processRequest(RequestObject request) {

		String timeSeriesUniqueId = request.getUniqueId();
		ResultObject result = new ResultObject();

		// don't interact with the databases if the incoming time series unique id is null
		if (null != timeSeriesUniqueId) {

			// get time series from transform db
			List<TimeSeries> timeSeriesList = transformDao.getTimeSeries(timeSeriesUniqueId);

			if (0 == timeSeriesList.size()) {
				// do not try to delete or insert rows if no data is returned from the get
				result.setStatus(LoadTimeSeries.STATUS_FAIL);
				LOG.debug("No records found for time series unique id: {}", timeSeriesUniqueId);
			} else {
				// otherwise, try to insert new time series or replace existing ones
				loadTimeSeriesIntoObservationDb(timeSeriesList, result, timeSeriesUniqueId);
			}
		} else {
			result.setStatus(STATUS_FAIL);
			LOG.debug("Time series unique id was null");
		}
		return result;
	}

	/**
	 * Inserts or replaces time series into the observation database.
	 * @param timeSeriesList the list of time series returned from the transform db
	 * @param result the status and count of inserted records
	 * @param timeSeriesUniqueId the unique id used to filter records for the get and the delete.
	 */
	public void loadTimeSeriesIntoObservationDb (List<TimeSeries> timeSeriesList, ResultObject result, String timeSeriesUniqueId) {
		// first delete existing time series from observation db
		observationDao.deleteTimeSeries(timeSeriesUniqueId);

		Integer count = 0;
		for (TimeSeries ts : timeSeriesList) {
			// insert time series into observation db
			count += observationDao.insertTimeSeries(ts);
		}
		result.setCount(count);

		if (count == timeSeriesList.size() && count != 0) {
			result.setStatus(STATUS_SUCCESS);
			LOG.debug("Successfully inserted time series with unique id: {} ", timeSeriesUniqueId);
		} else {
			result.setStatus(STATUS_FAIL);
			LOG.debug("Selected row count: {} and inserted row count: {} differ, insert failed.", timeSeriesList.size(), count);
		}
	}
}