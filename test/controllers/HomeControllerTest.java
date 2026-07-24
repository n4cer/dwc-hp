package controllers;

import org.junit.Test;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.mvc.Http;
import play.mvc.Result;
import play.test.WithApplication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static play.mvc.Http.Status.OK;
import static play.mvc.Http.Status.NOT_FOUND;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.GET;
import static play.test.Helpers.route;

public class HomeControllerTest extends WithApplication {

    @Override
    protected Application provideApplication() {
        return new GuiceApplicationBuilder().build();
    }

    @Test
    public void testIndex() {
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method(GET)
                .uri("/");

        Result result = route(app, request);
        assertEquals(OK, result.status());
        assertTrue(result.header("Content-Security-Policy").isPresent());
        assertTrue(contentAsString(result).contains("<meta name=\"description\""));
        assertTrue(contentAsString(result).contains("<link rel=\"canonical\" href=\"/\""));
    }

    @Test
    public void testRobotsAdvertisesSitemap() {
        Result result = route(app, new Http.RequestBuilder().method(GET).uri("/robots.txt"));

        assertEquals(OK, result.status());
        assertTrue(contentAsString(result).contains("Sitemap: https://www.doomwarriors.de/sitemap.xml"));
        assertTrue(contentAsString(result).contains("Disallow: /admin"));
    }

    @Test
    public void testSitemapContainsPublicPages() {
        Result result = route(app, new Http.RequestBuilder().method(GET).uri("/sitemap.xml"));
        String body = contentAsString(result);

        assertEquals(OK, result.status());
        assertTrue(body.contains("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">"));
        assertTrue(body.contains("<loc>https://www.doomwarriors.de/</loc>"));
        assertTrue(body.contains("<loc>https://www.doomwarriors.de/clanwars</loc>"));
    }

    @Test
    public void testMissingPublicEntitiesReturn404() {
        Result clanwar = route(app, new Http.RequestBuilder().method(GET).uri("/clanwar/999999999"));
        Result player = route(app, new Http.RequestBuilder().method(GET).uri("/player/999999999"));

        assertEquals(NOT_FOUND, clanwar.status());
        assertEquals(NOT_FOUND, player.status());
    }

}
