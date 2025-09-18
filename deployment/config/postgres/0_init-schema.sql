/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

CREATE SCHEMA IF NOT EXISTS qalipsis_liquibase;
ALTER
SCHEMA qalipsis_liquibase OWNER TO qalipsis;
CREATE SCHEMA IF NOT EXISTS qalipsis;
ALTER
SCHEMA qalipsis OWNER TO qalipsis;
CREATE SCHEMA IF NOT EXISTS events;
ALTER
SCHEMA events OWNER TO qalipsis;
CREATE SCHEMA IF NOT EXISTS meters;
ALTER
SCHEMA meters OWNER TO qalipsis;