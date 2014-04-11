package org.benjp.chat.stress 
import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Predef._
import com.excilys.ebi.gatling.jdbc.Predef._
import com.excilys.ebi.gatling.http.Headers.Names._
import akka.util.duration._
import bootstrap._
import assertions._
import scala.math.abs;
import scala.util.Random;

class PublicChatFullSimulation extends Simulation {

	val httpConf = httpConfig
		.baseURL("http://127.0.0.1:8280")
		.acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
		.acceptEncodingHeader("gzip, deflate")
		.acceptLanguageHeader("fr,fr-fr;q=0.8,en-us;q=0.5,en;q=0.3")
		.connection("keep-alive")
		.userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:19.0) Gecko/20100101 Firefox/19.0")

	val scnInit = scenario("Scenario : Init")
		.exec(http("server init")
			.get("/chatServer/")
		)
		.exec(http("drop db")
			.get("/chatServer/dropDB")
			.queryParam("db", "gatling")
			.queryParam("passphrase", "chat")
		)
    .pause(500 milliseconds)
		.exec(http("init db")
			.get("/chatServer/initDB")
			.queryParam("db", "gatling")
			.queryParam("passphrase", "chat")
		)
    .pause(500 milliseconds)
    .exec(http("ensure indexes")
      .get("/chatServer/ensureIndexes")
      .queryParam("db", "gatling")
      .queryParam("passphrase", "chat")
    )
    .pause(500 milliseconds)
		.repeat(500, "userId") {
			exec(http("create user")
				.get("/chatServer/createDemoUser")
				.queryParam("username", "user${userId}")
				.queryParam("passphrase", "chat")
			)			
			.exec(http("create spaces")
				.get("/chatServer/createDemoSpaces")
				.queryParam("username", "user${userId}")
				.queryParam("nbSpaces", "5")
				.queryParam("nbSpacesMax", "100")
				.queryParam("username", "user${userId}")
				.queryParam("passphrase", "chat")
			)
		}

	val iUser = new java.util.concurrent.atomic.AtomicInteger(0)
  val random = new scala.util.Random

	val scnSendMessages = scenario("Scenario : Messages")
		.exec((session:Session) => session.setAttribute("userId", iUser.getAndIncrement))
		.exec(http("get token")
			.get("/chatServer/getToken")
			.queryParam("username", "user${userId}")
			.queryParam("passphrase", "chat")
						//  "token": "45b2b",
			.check(regex("""token": "(.+)",""").saveAs("token"))
		)
    .repeat("1", "cptRoom") {
      exec((session:Session) => session.setAttribute("loopMsg", random.nextInt(200)+500))  // loop between 250 and 1000
      .randomSwitch(
        20 ->
          exec((session:Session) => session.setAttribute("targetUserId", "space-demo-"+scala.math.abs(random.nextInt(100))))
        ,
        80 ->
          exec((session:Session) => session.setAttribute("targetUserId", "user"+scala.math.abs(random.nextInt(500))))
      )
      .exec(http("get room")
        .get("/chatServer/getRoom")
        .queryParam("user", "user${userId}")
        .queryParam("token", "${token}")
        .queryParam("targetUser", "${targetUserId}")
        .queryParam("isAdmin", "false")
        .check(regex("""(.+)""").saveAs("room"))
      )
      .during( 10 minutes , "cpt") { // loop between 250 and 1000
//      .repeat( "${loopMsg}" , "cpt") { // loop between 250 and 1000
        randomSwitch(
          20 ->
            exec(http("send message")
              .get("/chatServer/send")
              .queryParam("user", "user${userId}")
              .queryParam("targetUser", "${targetUserId}")
              .queryParam("token", "${token}")
              .queryParam("room", "${room}")
              .queryParam("message", "This is user gatlin message : ${cpt}")
            )
          ,
          24 ->
            exec(http("who is online")
              .get("/chatServer/whoIsOnline")
              .queryParam("user", "user${userId}")
              .queryParam("token", "${token}")
              .queryParam("filter", "")
              .queryParam("limit", "20")
              .queryParam("isAdmin", "false")
            )
          ,
          1 ->
            exec(http("clear notifications")
              .get("/chatServer/updateUnreadTestMessages")
              .queryParam("username", "user${userId}")
              .queryParam("room", "ALL")
              .queryParam("passphrase", "chat")
            )
          ,
          1 ->
            exec(http("toggle favorite")
              .get("/chatServer/toggleFavorite")
              .queryParam("user", "user${userId}")
              .queryParam("targetUser", "${targetUserId}")
              .queryParam("token", "${token}")
            )
          ,
          3 ->
            exec(http("get status")
              .get("/chatServer/getStatus")
              .queryParam("user", "user${userId}")
              .queryParam("token", "${token}")
            )
          ,
          1 ->
            exec(http("set status")
              .get("/chatServer/setStatus")
              .queryParam("user", "user${userId}")
              .queryParam("token", "${token}")
              .queryParam("status", "away")
            )
          ,
          5 ->
            exec(http("update unread")
              .get("/chatServer/updateUnreadTestMessages")
              .queryParam("username", "user${userId}")
              .queryParam("room", "${room}")
              .queryParam("passphrase", "chat")
            )
          ,
          5 ->
            exec(http("update unread target")
              .get("/chatServer/updateUnreadTestMessages")
              .queryParam("username", "${targetUserId}")
              .queryParam("room", "${room}")
              .queryParam("passphrase", "chat")
            )
          ,
          40 ->
            exec(http("read message")
              .get("/chatServer/read")
              .queryParam("user", "user${userId}")
              .queryParam("targetUser", "${targetUserId}")
              .queryParam("token", "${token}")
              .queryParam("room", "${room}")
            )
        )
        .pause(200 milliseconds, 600 milliseconds)
      }

    }

	setUp(
		scnInit.users(1).protocolConfig(httpConf)
    , scnSendMessages.users(500).ramp(90).delay(5).protocolConfig(httpConf)
		)
}