package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import play.libs.Json;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ClanwarDemoStatsTest {

    // Shape of n4bot's GET /api/q3/clanwars/{id}, reduced to what the page reads.
    private static final String PAYLOAD = """
        {"clanwar_id": 463, "maps": [{
          "map": "q3wcp1",
          "primary_demo": {"id": "a", "url": "https://n4bot/q3/results/a", "demo_taker": "dwc.n4cer"},
          "demos": [{"id": "a", "url": "https://n4bot/q3/results/a", "demo_taker": "dwc.n4cer"},
                    {"id": "b", "url": "https://n4bot/q3/results/b", "demo_taker": "dwc.pons"}],
          "match": {
            "score_summary": "RED 3 : 0 BLUE", "duration_seconds": 1200, "has_match_stats": true,
            "players": [
              {"name": "DX!foo", "team": "blue", "score": 10, "kills": 4, "deaths": 9,
               "damage_given": 2100, "damage_received": 5200, "weapons": []},
              {"name": "dwc.n4cer", "team": "red", "score": 40, "kills": 12, "deaths": 4,
               "damage_given": 6400, "damage_received": 2900, "accuracy": 31,
               "weapons": [{"weapon": "Railgun", "accuracy": 44, "hits": 27, "shots": 61, "kills": 7}]},
              {"name": "dwc.pons", "team": "red", "score": 20, "kills": 6, "deaths": 0}
            ],
            "obituaries": [
              {"attacker": "dwc.n4cer", "target": "DX!foo", "cause": "railgun"},
              {"attacker": "dwc.n4cer", "target": "DX!foo", "cause": "rocket"},
              {"attacker": null, "target": "dwc.n4cer", "cause": "fall"}
            ]
          }
        }]}
        """;

    private static ClanwarDemoStats parse(Map<String, Quake3Stats.Identity> aliases) {
        JsonNode json = Json.parse(PAYLOAD);
        return ClanwarDemoStats.fromJson(json, aliases);
    }

    @Test
    public void readsPlayersSortedByTeamAndScore() {
        ClanwarDemoStats.MapStats map = parse(Map.of()).maps().get(0);

        assertEquals("q3wcp1", map.map());
        assertEquals("20:00", map.duration());
        assertEquals("https://n4bot/q3/results/a", map.demoUrl());
        assertEquals(List.of("dwc.n4cer", "dwc.pons", "DX!foo"),
                map.players().stream().map(ClanwarDemoStats.PlayerRow::name).toList());
        assertEquals(List.of("dwc.n4cer", "dwc.pons"), map.povs().stream().map(ClanwarDemoStats.Pov::demoTaker).toList());
    }

    @Test
    public void missingDamageStaysEmptyInsteadOfZero() {
        ClanwarDemoStats.PlayerRow pons = parse(Map.of()).maps().get(0).players().get(1);

        assertNull(pons.damageGiven());
        assertNull(pons.net());
        assertEquals("6.00", pons.kd());
    }

    @Test
    public void netDamageAndWeaponsOfAPlayer() {
        ClanwarDemoStats.MapStats map = parse(Map.of()).maps().get(0);
        ClanwarDemoStats.PlayerRow n4cer = map.players().get(0);

        assertEquals("+3500", n4cer.net());
        assertEquals(List.of("Railgun"), map.weapons());
        assertEquals(Integer.valueOf(27), n4cer.weapons().get("Railgun").hits());
        assertTrue(map.hasWeapons());
    }

    @Test
    public void killMatrixCountsKillsButNotWorldDeaths() {
        ClanwarDemoStats.MapStats map = parse(Map.of()).maps().get(0);

        assertEquals(List.of("dwc.n4cer", "dwc.pons", "DX!foo"), map.matrixNames());
        assertEquals(List.of(0, 0, 2), map.matrix().get(0));
        assertEquals(List.of(0, 0, 0), map.matrix().get(2));
    }

    @Test
    public void inGameNamesAreLinkedToMembersWithAndWithoutClanTag() {
        Quake3Stats.Identity n4cer = new Quake3Stats.Identity("n4cer", 7L, List.of());
        Quake3Stats.Identity pons = new Quake3Stats.Identity("pons", 9L, List.of("dwc.pons"));

        List<ClanwarDemoStats.PlayerRow> players = parse(Quake3Stats.aliases(List.of(n4cer, pons))).maps().get(0).players();

        assertEquals(Long.valueOf(7), players.get(0).memberId());
        assertEquals("n4cer", players.get(0).shownName());
        assertEquals(Long.valueOf(9), players.get(1).memberId());
        assertNull(players.get(2).memberId());
        assertFalse(players.get(2).shownName().isEmpty());
    }
}
