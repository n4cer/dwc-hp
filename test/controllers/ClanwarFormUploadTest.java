package controllers;

import io.ebean.DB;
import models.Clanwar;
import models.Country;
import models.Game;
import models.GameType;
import models.League;
import models.Role;
import models.Score;
import models.ScoreDemo;
import models.ScoreImage;
import models.User;
import models.UserRole;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.util.ByteString;
import org.junit.After;
import org.junit.Test;
import play.Application;
import play.api.test.CSRFTokenHelper;
import play.inject.guice.GuiceApplicationBuilder;
import play.libs.Files.TemporaryFileCreator;
import play.mvc.Http;
import play.mvc.Result;
import play.test.WithApplication;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static play.mvc.Http.Status.BAD_REQUEST;
import static play.mvc.Http.Status.SEE_OTHER;
import static play.test.Helpers.POST;
import static play.test.Helpers.route;

/** Needs PostgreSQL, like every WithApplication test (runs in CI). */
public class ClanwarFormUploadTest extends WithApplication {
    private static final String PREFIX = "uploadtest-";
    private Path pictures;
    private Path downloads;
    private final List<Object> created = new ArrayList<>();

    @Override
    protected Application provideApplication() {
        try {
            pictures = Files.createTempDirectory("dwc-pictures");
            downloads = Files.createTempDirectory("dwc-downloads");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        return new GuiceApplicationBuilder()
                .configure("picture_folder", pictures.toString())
                .configure("download_folder", downloads.toString())
                .build();
    }

    @After
    public void cleanUp() throws IOException {
        Clanwar.find.query().where().eq("enemy", PREFIX + "enemy").findList().forEach(Clanwar::delete);
        for (Object entity : created) DB.delete(entity);
        DB.sqlUpdate("delete from lineup where nick like :prefix").setParameter("prefix", PREFIX + "%").execute();
        for (Path folder : List.of(pictures, downloads)) {
            try (Stream<Path> files = Files.walk(folder)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    @Test
    public void screenshotsAndDemosAreStoredAndAttached() throws IOException {
        Files.createDirectories(pictures.resolve("clanwars"));
        Files.writeString(pictures.resolve("clanwars").resolve("q3dm6.png"), "older screenshot");
        Fixture fixture = fixture();

        Result result = post(fixture, List.of(
                file("newScoreImageUpload_n0", "q3dm6.png", "new screenshot"),
                file("newScoreImageUpload_n0", "rail shot.jpg", "second screenshot"),
                file("demoUpload_d0", "C:\\demos\\my demo.dm_68", "demo bytes")),
                "n4cer");

        assertEquals(SEE_OTHER, result.status());
        assertEquals("/admin/clanwars", result.redirectLocation().orElse(""));
        Score score = Score.find.query().where().eq("match.enemy", PREFIX + "enemy").findOne();
        List<String> images = ScoreImage.find.query().where().eq("score.id", score.getId()).orderBy("id").findList()
                .stream().map(ScoreImage::getImage).toList();
        assertEquals(List.of("q3dm6-1.png", "rail_shot.jpg"), images);
        assertEquals("older screenshot", Files.readString(pictures.resolve("clanwars").resolve("q3dm6.png")));
        assertEquals("new screenshot", Files.readString(pictures.resolve("clanwars").resolve("q3dm6-1.png")));

        ScoreDemo demo = ScoreDemo.find.query().where().eq("score.id", score.getId()).findOne();
        assertEquals("my_demo.dm_68", demo.getFile());
        assertEquals("n4cer", demo.getPlayer());
        assertEquals("demo bytes", Files.readString(downloads.resolve("clanwar-demos").resolve("my_demo.dm_68")));
    }

    @Test
    public void demosMayExceedPlaysDefaultLimit() throws IOException {
        Fixture fixture = fixture();
        String demo = "x".repeat(12 * 1024 * 1024);

        Result result = post(fixture, List.of(file("demoUpload_d0", "large.dm_68", demo)), "n4cer");

        assertEquals("/admin/clanwars", result.redirectLocation().orElse(""));
        assertEquals(demo.length(), Files.size(downloads.resolve("clanwar-demos").resolve("large.dm_68")));
    }

    @Test
    public void refusedUploadsSaveNothing() throws IOException {
        Fixture fixture = fixture();

        Result wrongType = post(fixture, List.of(file("newScoreImageUpload_n0", "notes.txt", "text")), "n4cer");
        Result noRecorder = post(fixture, List.of(file("demoUpload_d0", "final.dm_68", "demo bytes")), "");

        assertEquals(BAD_REQUEST, wrongType.status());
        assertEquals(BAD_REQUEST, noRecorder.status());
        assertEquals(0, Clanwar.find.query().where().eq("enemy", PREFIX + "enemy").findCount());
        assertTrue(!Files.exists(downloads.resolve("clanwar-demos").resolve("final.dm_68")));
    }

    private record Fixture(User member, Game game, GameType gameType, League league, Country country, models.Map map) { }

    private Fixture fixture() {
        User member = new User();
        member.setNick(PREFIX + "member");
        member.setActive(true);
        member.setPasswordHash(Passwords.hash("a long enough password"));
        member.setPasswordChangedAt(new Date());
        member.save();
        UserRole role = new UserRole();
        role.setMember(member);
        role.setRole(Role.CLANWARS);
        role.save();
        member.refresh();

        Game game = new Game();
        game.setShortText("ut");
        game.setDescription(PREFIX + "game");
        game.save();
        GameType gameType = new GameType();
        gameType.setGameType(PREFIX + "tdm");
        gameType.save();
        League league = new League();
        league.setLeague(PREFIX + "league");
        league.save();
        Country country = new Country();
        country.setCountry(PREFIX + "country");
        country.setShortText("ut");
        country.save();
        models.Map map = new models.Map();
        map.setMap(PREFIX + "q3dm6");
        map.setGame(game);
        map.save();
        created.addAll(List.of(role, map, country, league, gameType, game));
        return new Fixture(member, game, gameType, league, country, map);
    }

    private Result post(Fixture fixture, List<Http.MultipartFormData.Part<Source<ByteString, ?>>> files, String recorder) {
        List<Http.MultipartFormData.Part<Source<ByteString, ?>>> parts = new ArrayList<>(List.of(
                data("enemy", PREFIX + "enemy"),
                data("date", "2026-10-28T20:30"),
                data("gameId", fixture.game().getId()),
                data("gameTypeId", fixture.gameType().getId()),
                data("leagueId", fixture.league().getId()),
                data("countryId", fixture.country().getId()),
                data("newScoreKey", "n0"),
                data("newScoreMap_n0", fixture.map().getId()),
                data("newScoreDwc_n0", 3),
                data("newScoreEnemy_n0", 1),
                data("newScoreDemo_n0File", ""),
                data("newScoreDemo_n0Player", recorder),
                data("newScoreDemo_n0Upload", "d0")));
        parts.addAll(files);
        Http.RequestBuilder request = new Http.RequestBuilder().method(POST).uri("/admin/clanwars")
                .session(AdminAuth.SESSION_MEMBER, String.valueOf(fixture.member().getId()))
                .session(AdminAuth.SESSION_VERSION, AdminAuth.sessionVersion(fixture.member()))
                .bodyRaw(parts, app.injector().instanceOf(TemporaryFileCreator.class),
                        app.injector().instanceOf(Materializer.class));
        return route(app, CSRFTokenHelper.addCSRFToken(request));
    }

    private static Http.MultipartFormData.Part<Source<ByteString, ?>> data(String key, Object value) {
        return new Http.MultipartFormData.DataPart(key, String.valueOf(value));
    }

    private static Http.MultipartFormData.Part<Source<ByteString, ?>> file(String key, String name, String content) {
        return new Http.MultipartFormData.FilePart<>(key, name, "application/octet-stream",
                Source.single(ByteString.fromString(content, StandardCharsets.UTF_8)));
    }
}
