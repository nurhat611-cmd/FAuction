package fr.florianpal.fauction.queries;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.enums.SQLType;
import fr.florianpal.fauction.managers.DatabaseManager;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * The per-player data that has to outlive the session and the restart, one row per player.
 * <p>
 * Only the free-commission counter lives here for now : how many lots the player has already
 * listed without commission. It is the memory of the {@code fauction.nocommission.<n>} quota in
 * {@code LIFETIME} mode, and the one thing a wipe ({@code /ah clear data players}) has to zero
 * on top of the auctions, the expired items and the history. A row only exists for a player who
 * spent at least one free listing ; no row reads as zero.
 */
public class PlayerProfileQueries implements IDatabaseTable {

    private final FAuction plugin;

    private final DatabaseManager databaseManager;

    private static final String GET_USED_FREE_LISTINGS = "SELECT usedFreeListings FROM fa_player_profiles WHERE playerUuid=?";
    private static final String GET_SOUNDS_ENABLED = "SELECT soundsEnabled FROM fa_player_profiles WHERE playerUuid=?";
    private static final String UPDATE_SOUNDS_ENABLED = "UPDATE fa_player_profiles SET playerName=?, soundsEnabled=? WHERE playerUuid=?";
    private static final String ADD_PROFILE = "INSERT INTO fa_player_profiles (playerUuid, playerName, usedFreeListings, soundsEnabled) VALUES(?,?,?,?)";
    private static final String INCREMENT_USED_FREE_LISTINGS = "UPDATE fa_player_profiles SET playerName=?, usedFreeListings=usedFreeListings+1 WHERE playerUuid=?";
    private static final String DELETE_ALL = "DELETE FROM fa_player_profiles";

    private String parameters = "DEFAULT CHARACTER SET utf8 COLLATE utf8_general_ci";

    private String trueLiteral = "1";

    public PlayerProfileQueries(FAuction plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        if (plugin.getConfigurationManager().getDatabase().getSqlType() == SQLType.SQLite
                || plugin.getConfigurationManager().getDatabase().getSqlType() == SQLType.PostgreSQL) {
            parameters = "";
        }
        if (plugin.getConfigurationManager().getDatabase().getSqlType() == SQLType.PostgreSQL) {
            trueLiteral = "TRUE";
        }
    }

    /**
     * How many free listings the player has spent so far. Zero when the player has no row yet.
     */
    public int getUsedFreeListings(UUID playerUuid) {
        try (Connection connection = databaseManager.getConnection()) {
            return getUsedFreeListings(connection, playerUuid);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error when reading the free-listing counter of " + playerUuid + ". Error " + e.getMessage());
        }
        return 0;
    }

    private int getUsedFreeListings(Connection connection, UUID playerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(GET_USED_FREE_LISTINGS)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    /**
     * Spends one free listing, creating the row of the player on their first one.
     * <p>
     * The update runs first and the insert only fills the hole : the same upsert spelled with
     * {@code ON CONFLICT} / {@code ON DUPLICATE KEY} would need one spelling per database. The
     * two writes share one connection, and two sales of the same player cannot be in flight at
     * the same time (the ClaimManager reservation of {@code /ah sell}), so the row cannot be
     * created twice by the same server.
     *
     * @return the counter after the increment
     */
    public int incrementUsedFreeListings(UUID playerUuid, String playerName) {
        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(INCREMENT_USED_FREE_LISTINGS)) {
                statement.setString(1, playerName);
                statement.setString(2, playerUuid.toString());
                if (statement.executeUpdate() > 0) {
                    return getUsedFreeListings(connection, playerUuid);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(ADD_PROFILE)) {
                statement.setString(1, playerUuid.toString());
                statement.setString(2, playerName);
                statement.setInt(3, 1);
                statement.setBoolean(4, true);
                statement.executeUpdate();
                return 1;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error when incrementing the free-listing counter of " + playerName + ". Error " + e.getMessage());
        }
        return 0;
    }

    /**
     * Whether the auction sounds are on for this player ({@code /ah sounds on|off}). True when
     * the player has no row yet : sounds are on until switched off.
     */
    public boolean getSoundsEnabled(UUID playerUuid) {
        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(GET_SOUNDS_ENABLED)) {
                statement.setString(1, playerUuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return !result.next() || result.getBoolean(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error when reading the sound switch of " + playerUuid + ". Error " + e.getMessage());
        }
        return true;
    }

    /**
     * Stores the {@code /ah sounds on|off} switch, creating the row of a player who flips it
     * before ever spending a free listing. Same update-then-insert upsert as the counter.
     */
    public void setSoundsEnabled(UUID playerUuid, String playerName, boolean enabled) {
        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(UPDATE_SOUNDS_ENABLED)) {
                statement.setString(1, playerName);
                statement.setBoolean(2, enabled);
                statement.setString(3, playerUuid.toString());
                if (statement.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(ADD_PROFILE)) {
                statement.setString(1, playerUuid.toString());
                statement.setString(2, playerName);
                statement.setInt(3, 0);
                statement.setBoolean(4, enabled);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error when saving the sound switch of " + playerName + ". Error " + e.getMessage());
        }
    }

    /**
     * Adds the {@code soundsEnabled} column to a profile table created before the sounds
     * existed. Same reasoning as {@code AuctionQueries.ensureCommissionFreeColumn()} : the
     * selects read the column, so it has to be there before the first read, and a fresh table
     * already has it.
     */
    public void ensureSoundsEnabledColumn() {
        try (Connection connection = databaseManager.getConnection()) {
            if (columnExists(connection, "soundsEnabled") || columnExists(connection, "soundsenabled")) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE fa_player_profiles ADD COLUMN soundsEnabled BOOLEAN NOT NULL DEFAULT " + trueLiteral);
            }
            plugin.getLogger().info("The fa_player_profiles table gained its soundsEnabled column !");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error when adding the soundsEnabled column to the fa_player_profiles table. Error " + e.getMessage());
        }
    }

    private boolean columnExists(Connection connection, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet columns = metaData.getColumns(null, null, "fa_player_profiles", columnName)) {
            return columns.next();
        }
    }

    /**
     * The wipe : every counter back to zero, by removing the rows (no row reads as zero).
     */
    public void deleteAll() {
        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(DELETE_ALL)) {
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error when deleting all player profiles. Error " + e.getMessage());
        }
    }

    @Override
    public String[] getTable() {
        return new String[]{"fa_player_profiles",
                "`playerUuid` VARCHAR(36) NOT NULL PRIMARY KEY, " +
                        "`playerName` VARCHAR(36) NOT NULL, " +
                        "`usedFreeListings` INT NOT NULL, " +
                        "`soundsEnabled` BOOLEAN NOT NULL DEFAULT " + trueLiteral,
                parameters
        };
    }
}
