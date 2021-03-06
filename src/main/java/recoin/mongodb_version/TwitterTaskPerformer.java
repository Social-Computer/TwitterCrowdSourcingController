package recoin.mongodb_version;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;

import org.apache.commons.lang3.builder.CompareToBuilder;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.bson.Document;
import org.bson.types.ObjectId;

import sociam.pybossa.config.Config;
import sociam.pybossa.methods.GeneralMethods;
import sociam.pybossa.methods.MongodbMethods;
import sociam.pybossa.methods.TwitterMethods;

/**
 * 
 * @author user Saud Aljaloud
 * @author email sza1g10@ecs.soton.ac.uk
 *
 */
public class TwitterTaskPerformer {

	final static Logger logger = Logger.getLogger(TwitterTaskPerformer.class);
	final static SimpleDateFormat MongoDBformatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	static Boolean wasPushed = false;

	public static void main(String[] args) {
		PropertyConfigurator.configure("log4j.properties");
		logger.info("TaskPerformer will be repeated every " + Config.TaskCreatorTrigger + " ms");
		try {
			while (true) {
				Config.reload();
				run();
				logger.info("Sleeping for " + Config.TaskPerformerTrigger + " ms");
				Thread.sleep(Integer.valueOf(Config.TaskPerformerTrigger));
			}
		} catch (InterruptedException e) {
			logger.error("Error ", e);
		}
	}

	public static void run() {
		try {

			// add counter for tasks as task_id
			logger.debug("Adding task_id field to collection "
					+ Config.taskCollection);
			MongodbMethods.updateTasksByAddingCounters();

			ArrayList<Document> tasksToBePushed = MongodbMethods
					.getIncompletedTasksFromMongoDB("twitter_task_status");
			if (tasksToBePushed != null) {
				logger.info("There are "
						+ tasksToBePushed.size()
						+ " tasks that need to be pushed into Twitter, then updating to MongoDB");

				// randomly pick a task
				// for (Document document : tasksToBePushed) {
				Queue<Document> queue = stackTwitterQueue(tasksToBePushed);
				for (Document document : queue) {
					logger.debug("The queue size is " + queue.size()
							+ " Task to be processed " + document.toString());
					String twitter_task_status = document
							.getString("twitter_task_status");
					String task_text = document.getString("task_text");
					Integer task_id = document.getInteger("task_id");
					Integer pushing_times = document
							.getInteger("pushing_times");
					if (twitter_task_status.equals("pushed")) {
						if (pushing_times > Integer
								.valueOf(Config.pushinglimit)) {
							// move task to zombie state
							Boolean result = MongodbMethods
									.updateTaskToBeCompleted(task_id);
							if (result) {
								logger.debug("Moved task to be completed "
										+ document.toString());
							} else {
								logger.error("Task was not being updated to be completed "
										+ document.toString());
							}
							continue;
						}
						String twitter_lastPushAtString = document
								.getString("twitter_lastPushAt");
						Date twitter_lastPushAt = MongoDBformatter
								.parse(twitter_lastPushAtString);
						if (!GeneralMethods.rePush(twitter_lastPushAt)) {
							continue;
						} else {
							logger.debug("Repushing task " + task_text);
						}
					}
					ObjectId _id = document.getObjectId("_id");
					int project_id = document.getInteger("project_id");
					String twitter_url = document.getString("twitter_url");
					String redirect_tweet_id = null;
					logger.debug("twitter_url " + twitter_url);
					if (twitter_url == null) {
						logger.error("task does not contain twitter_url");
						continue;
					} else {
						redirect_tweet_id = TwitterMethods
								.redirectStatua(twitter_url);
						if (redirect_tweet_id == null) {
							logger.error("coundn't resolve stored tweet_url to its orginal url");
							continue;
						}
						logger.debug("redirect_tweet_id " + redirect_tweet_id);
					}
					ArrayList<String> hashtags = MongodbMethods
							.getProjectHashTags(project_id);
					String taskTag = "#t" + task_id;
					int responseCode = TwitterMethods.sendTaskToTwitterWithUrl(
							taskTag, hashtags, 2, null, redirect_tweet_id);
					if (responseCode == 1) {
						if (MongodbMethods.updateTaskToPushedInMongoDB(_id,
								"pushed", pushing_times)) {
							logger.info("Task with text " + task_text
									+ " has been sucessfully pushed to Twitter");
							wasPushed = true;
						} else {
							logger.error("Error with updating "
									+ Config.taskCollection + " for the _id "
									+ _id.toString());
						}
					} else if (responseCode == 0) {
						if (MongodbMethods.updateTaskToPushedInMongoDB(_id,
								"notValied", pushing_times)) {
							logger.debug("Tweeet is not valid because of length, but updated in Mongodb"
									+ task_text);
						}
						logger.error("Couldn't update the task in MongoDB");
					} else if (responseCode == 2) {
						if (MongodbMethods.updateTaskToPushedInMongoDB(_id,
								"error", pushing_times)) {
							logger.debug("pushing tweet has encountered an error, but has been updated into MongoDB "
									+ task_text);
						} else {
							logger.error("Couldn't update the task in MongoDB");
						}
					} else if (responseCode == 3) {
						continue;
					}
					// TODO: add lastPushAt when updating tasks

					if (wasPushed) {
						wasPushed = false;
						ArrayList<Document> pushedTasks = MongodbMethods
								.getPushedTasks("twitter_task_status",
										"pushed", Config.taskCollection);
						Integer pushedTasksNo = pushedTasks.size();
						ArrayList<Document> collectedTaskRuns = MongodbMethods
								.getPushedTasks("source", "Twitter",
										Config.taskRunCollection);
						Integer collectedTaskRunsNo = collectedTaskRuns.size();
						float calcuatedWaitingTime = GeneralMethods
								.getPusingTime(
										Float.parseFloat(Config.topSpeed),
										Float.parseFloat(Config.lowestSpeed),
										Float.parseFloat(Config.firstLimit),
										pushedTasksNo, collectedTaskRunsNo);
						logger.debug("waiting for " + calcuatedWaitingTime
								+ " minutes before pushing another tweet");
						Thread.sleep((long) (calcuatedWaitingTime * 60 * 1000));
					}
				}
			}
		} catch (Exception e) {
			logger.error("Error ", e);
		}
	}

	public static Queue<Document> stackTwitterQueue(ArrayList<Document> tasksToBePushed) {

		Collections.sort(tasksToBePushed, new Comparator<Document>() {
			@Override
			public int compare(Document p1, Document p2) {
				return new CompareToBuilder().append(p2.getInteger("priority"), p1.getInteger("priority"))
						.append(p2.getString("twitter_task_status"), p1.getString("twitter_task_status"))
						.append(p2.getString("task_text").length(), p1.getString("task_text").length()).toComparison();
			}
		});
		Queue<Document> queue = new LinkedList<Document>(tasksToBePushed);
		return queue;
	}

}
