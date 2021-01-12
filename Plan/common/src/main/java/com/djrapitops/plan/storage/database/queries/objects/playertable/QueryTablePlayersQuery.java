/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Plan is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Plan. If not, see <https://www.gnu.org/licenses/>.
 */
package com.djrapitops.plan.storage.database.queries.objects.playertable;

import com.djrapitops.plan.delivery.domain.TablePlayer;
import com.djrapitops.plan.delivery.domain.mutators.ActivityIndex;
import com.djrapitops.plan.storage.database.SQLDB;
import com.djrapitops.plan.storage.database.queries.Query;
import com.djrapitops.plan.storage.database.queries.QueryStatement;
import com.djrapitops.plan.storage.database.queries.analysis.NetworkActivityIndexQueries;
import com.djrapitops.plan.storage.database.sql.tables.GeoInfoTable;
import com.djrapitops.plan.storage.database.sql.tables.SessionsTable;
import com.djrapitops.plan.storage.database.sql.tables.UserInfoTable;
import com.djrapitops.plan.storage.database.sql.tables.UsersTable;
import org.apache.commons.text.TextStringBuilder;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static com.djrapitops.plan.storage.database.sql.building.Sql.*;

/**
 * Query for displaying players on /query page players table.
 *
 * @author Rsl1122
 */
public class QueryTablePlayersQuery implements Query<List<TablePlayer>> {

    private final Collection<UUID> playerUUIDs;
    private final long afterDate;
    private final long beforeDate;
    private final long activeMsThreshold;

    /**
     * Create a new query.
     *
     * @param playerUUIDs       UUIDs of the players in the query
     * @param beforeDate        Date used for Activity Index calculation
     * @param activeMsThreshold Playtime threshold for Activity Index calculation
     */
    public QueryTablePlayersQuery(Collection<UUID> playerUUIDs, long afterDate, long beforeDate, long activeMsThreshold) {
        this.playerUUIDs = playerUUIDs;
        this.afterDate = afterDate;
        this.beforeDate = beforeDate;
        this.activeMsThreshold = activeMsThreshold;
    }

    @Override
    public List<TablePlayer> executeQuery(SQLDB db) {
        String uuidsInSet = " IN ('" + new TextStringBuilder().appendWithSeparators(playerUUIDs, "','").build() + "')";

        String selectGeolocations = SELECT + DISTINCT +
                GeoInfoTable.USER_UUID + ", " +
                GeoInfoTable.GEOLOCATION + ", " +
                GeoInfoTable.LAST_USED +
                FROM + GeoInfoTable.TABLE_NAME;
        String selectLatestGeolocationDate = SELECT +
                GeoInfoTable.USER_UUID + ", " +
                "MAX(" + GeoInfoTable.LAST_USED + ") as last_used_g" +
                FROM + GeoInfoTable.TABLE_NAME +
                GROUP_BY + GeoInfoTable.USER_UUID;
        String selectLatestGeolocations = SELECT +
                "g1." + GeoInfoTable.GEOLOCATION + ',' +
                "g1." + GeoInfoTable.USER_UUID +
                FROM + "(" + selectGeolocations + ") AS g1" +
                INNER_JOIN + "(" + selectLatestGeolocationDate + ") AS g2 ON g1.uuid = g2.uuid" +
                WHERE + GeoInfoTable.LAST_USED + "=last_used_g";

        String selectSessionData = SELECT + "s." + SessionsTable.USER_UUID + ',' +
                "MAX(" + SessionsTable.SESSION_END + ") as last_seen," +
                "COUNT(1) as count," +
                "SUM(" + SessionsTable.SESSION_END + '-' + SessionsTable.SESSION_START + ") as playtime" +
                FROM + SessionsTable.TABLE_NAME + " s" +
                WHERE + "s." + SessionsTable.SESSION_START + ">=?" +
                AND + "s." + SessionsTable.SESSION_END + "<=?" +
                AND + "s." + SessionsTable.USER_UUID +
                uuidsInSet +
                GROUP_BY + "s." + SessionsTable.USER_UUID;

        String selectBaseUsers = SELECT +
                "u." + UsersTable.USER_UUID + ',' +
                "u." + UsersTable.USER_NAME + ',' +
                "u." + UsersTable.REGISTERED + ',' +
                UserInfoTable.BANNED + ',' +
                "geoloc." + GeoInfoTable.GEOLOCATION + ',' +
                "ses.last_seen," +
                "ses.count," +
                "ses.playtime," +
                "act.activity_index" +
                FROM + UsersTable.TABLE_NAME + " u" +
                INNER_JOIN + UserInfoTable.TABLE_NAME + " on u." + UsersTable.USER_UUID + "=" + UserInfoTable.TABLE_NAME + '.' + UserInfoTable.USER_UUID +
                LEFT_JOIN + '(' + selectLatestGeolocations + ") geoloc on geoloc." + GeoInfoTable.USER_UUID + "=u." + UsersTable.USER_UUID +
                LEFT_JOIN + '(' + selectSessionData + ") ses on ses." + SessionsTable.USER_UUID + "=u." + UsersTable.USER_UUID +
                LEFT_JOIN + '(' + NetworkActivityIndexQueries.selectActivityIndexSQL() + ") act on u." + SessionsTable.USER_UUID + "=act." + UserInfoTable.USER_UUID +
                WHERE + "u." + UserInfoTable.USER_UUID +
                uuidsInSet +
                ORDER_BY + "ses.last_seen DESC";

        return db.query(new QueryStatement<List<TablePlayer>>(selectBaseUsers, 1000) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                statement.setLong(1, afterDate);
                statement.setLong(2, beforeDate);
                NetworkActivityIndexQueries.setSelectActivityIndexSQLParameters(statement, 3, activeMsThreshold, beforeDate);
            }

            @Override
            public List<TablePlayer> processResults(ResultSet set) throws SQLException {
                List<TablePlayer> players = new ArrayList<>();
                while (set.next()) {
                    TablePlayer.Builder player = TablePlayer.builder()
                            .uuid(UUID.fromString(set.getString(UsersTable.USER_UUID)))
                            .name(set.getString(UsersTable.USER_NAME))
                            .geolocation(set.getString(GeoInfoTable.GEOLOCATION))
                            .registered(set.getLong(UsersTable.REGISTERED))
                            .lastSeen(set.getLong("last_seen"))
                            .sessionCount(set.getInt("count"))
                            .playtime(set.getLong("playtime"))
                            .activityIndex(new ActivityIndex(set.getDouble("activity_index"), beforeDate));
                    if (set.getBoolean(UserInfoTable.BANNED)) {
                        player.banned();
                    }
                    players.add(player.build());
                }
                return players;
            }
        });
    }
}