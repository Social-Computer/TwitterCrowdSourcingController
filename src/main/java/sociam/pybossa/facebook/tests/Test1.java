package sociam.pybossa.facebook.tests;

import java.io.File;

import facebook4j.Account;
import facebook4j.Facebook;
import facebook4j.FacebookException;
import facebook4j.FacebookFactory;
import facebook4j.Media;
import facebook4j.Page;
import facebook4j.PhotoUpdate;
import facebook4j.Post;
import facebook4j.PostUpdate;
import facebook4j.Reading;
import facebook4j.ResponseList;

public class Test1 {

	public static void main(String[] args) throws FacebookException {
		Facebook facebook = new FacebookFactory().getInstance();

		// System.out.println(facebook.postStatusMessage("post from app"));

		ResponseList<Post> feeds = facebook.getFeed("964602923577144",
				new Reading().limit(25));

		 for (Post post : feeds) {
		 System.out.println(post);
		 }

		// PostUpdate post = new PostUpdate("from app too page");
		// System.out.println(facebook.postFeed(post));

		// Media media = new Media(new File(
		// "/Users/user/Eclipse/PybossaTwitterController/textToImage.jpg"));
		// PhotoUpdate photoUpdate = new PhotoUpdate(media);
		// photoUpdate.message("tast with massge");
		// facebook.postPhoto(photoUpdate);

	}
}