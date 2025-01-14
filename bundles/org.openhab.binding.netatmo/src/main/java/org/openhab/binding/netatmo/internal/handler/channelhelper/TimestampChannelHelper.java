/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.netatmo.internal.handler.channelhelper;

import static org.openhab.binding.netatmo.internal.NetatmoBindingConstants.*;
import static org.openhab.binding.netatmo.internal.utils.ChannelTypeUtils.toDateTimeType;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.netatmo.internal.api.dto.NAThing;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.types.State;

/**
 * The {@link TimestampChannelHelper} handles specific behavior
 * of modules reporting last seen
 *
 * @author Gaël L'hopital - Initial contribution
 *
 */
@NonNullByDefault
public class TimestampChannelHelper extends ChannelHelper {

    public TimestampChannelHelper() {
        this(GROUP_TIMESTAMP);
    }

    protected TimestampChannelHelper(String groupName) {
        super(groupName);
    }

    @Override
    protected @Nullable State internalGetProperty(String channelId, NAThing naThing, Configuration config) {
        Optional<ZonedDateTime> lastSeen = naThing.getLastSeen();
        return CHANNEL_LAST_SEEN.equals(channelId) && lastSeen.isPresent() ? toDateTimeType(lastSeen) : null;
    }
}
