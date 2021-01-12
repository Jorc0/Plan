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
package com.djrapitops.plan.storage.database.queries.filter.filters;

import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.queries.filter.Filter;
import com.djrapitops.plan.storage.database.queries.filter.FilterQuery;
import com.djrapitops.plan.storage.database.queries.objects.BaseUserQueries;
import com.djrapitops.plan.utilities.java.Maps;
import org.apache.commons.lang3.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Map;

public abstract class DateRangeFilter implements Filter {

    private final DBSystem dbSystem;
    private final SimpleDateFormat dateFormat;

    public DateRangeFilter(DBSystem dbSystem) {
        this.dbSystem = dbSystem;
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy kk:mm");
    }

    @Override
    public String[] getExpectedParameters() {
        return new String[]{
                "afterDate",
                "afterTime",
                "beforeDate",
                "beforeTime"
        };
    }

    @Override
    public Map<String, Object> getOptions() {
        long earliestData = dbSystem.getDatabase().query(BaseUserQueries.minimumRegisterDate());
        long now = System.currentTimeMillis();
        if (earliestData == -1) earliestData = now;
        String[] afterDate = StringUtils.split(dateFormat.format(earliestData), ' ');
        String[] beforeDate = StringUtils.split(dateFormat.format(now), ' ');
        return Maps.builder(String.class, Object.class)
                .put("after", afterDate)
                .put("before", beforeDate)
                .build();
    }

    protected long getAfter(FilterQuery query) {
        return getTime(query, "afterDate", "afterTime");
    }

    protected long getBefore(FilterQuery query) {
        return getTime(query, "beforeDate", "beforeTime");
    }

    private long getTime(FilterQuery query, String dateKey, String timeKey) {
        String date = query.get(dateKey).orElseThrow(IllegalArgumentException::new);
        String time = query.get(timeKey).orElseThrow(IllegalArgumentException::new);

        try {
            return dateFormat.parse(date + ' ' + time).getTime();
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
