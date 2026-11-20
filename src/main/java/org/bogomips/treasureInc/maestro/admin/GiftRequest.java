package org.bogomips.treasureInc.maestro.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Body of {@code POST /admin/gifts}: a gift for the gift box of the players. Maestro only checks its shape: the
 * engine checks the values and answers 400 with what is wrong (see the README).
 *
 * @param title         what the player reads, e.g. "Lancement du jeu : profitez de ces gemmes gratuites !"
 * @param reward        what the gift gives
 * @param players       who receives it; null for every player
 * @param expiresInDays after how many days the gift can no longer be taken; null when it never expires
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GiftRequest(String title, Reward reward, Players players, Integer expiresInDays) {

    /**
     * @param items  how many items are generated when the gift is taken
     * @param rarity the rarity of these items (common, uncommon, rare, epic, legendary); null to draw each one as in
     *               a chest
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Reward(Integer gems, Integer money, Integer items, String rarity) {
    }

    /**
     * The players matching every criterion given.
     *
     * @param activeDays   played within the last days
     * @param inactiveDays have not played for these days
     * @param createdDays  started within the last days
     * @param publicKeys   these players only
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Players(Integer activeDays, Integer inactiveDays, Integer createdDays, List<String> publicKeys) {
    }
}
