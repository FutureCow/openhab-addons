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
import static org.openhab.binding.netatmo.internal.utils.ChannelTypeUtils.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.netatmo.internal.api.data.ModuleType;
import org.openhab.binding.netatmo.internal.api.data.NetatmoConstants.VideoStatus;
import org.openhab.binding.netatmo.internal.api.dto.Event;
import org.openhab.binding.netatmo.internal.api.dto.HomeEvent;
import org.openhab.binding.netatmo.internal.api.dto.NAObject;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

/**
 * The {@link EventChannelHelper} handles specific channels of cameras
 *
 * @author Gaël L'hopital - Initial contribution
 *
 */
@NonNullByDefault
public class EventChannelHelper extends ChannelHelper {
    private boolean isLocal;
    private @Nullable String vpnUrl, localUrl;
    private ModuleType moduleType = ModuleType.UNKNOWN;

    public EventChannelHelper() {
        this(GROUP_LAST_EVENT);
    }

    protected EventChannelHelper(String groupName) {
        super(groupName);
    }

    public void setModuleType(ModuleType moduleType) {
        this.moduleType = moduleType;
    }

    public void setUrls(String vpnUrl, @Nullable String localUrl) {
        this.localUrl = localUrl;
        this.vpnUrl = vpnUrl;
        this.isLocal = localUrl != null;
    }

    @Override
    public void setNewData(@Nullable NAObject data) {
        if (data instanceof Event) {
            Event event = (Event) data;
            if (!event.getEventType().appliesOn(moduleType)) {
                return;
            }
        }
        super.setNewData(data);
    }

    @Override
    protected @Nullable State internalGetEvent(String channelId, Event event) {
        switch (channelId) {
            case CHANNEL_EVENT_TYPE:
                return toStringType(event.getEventType());
            case CHANNEL_EVENT_MESSAGE:
                return toStringType(event.getName());
            case CHANNEL_EVENT_TIME:
                return new DateTimeType(event.getTime());
            case CHANNEL_EVENT_PERSON_ID:
                return toStringType(event.getPersonId());
            case CHANNEL_EVENT_CAMERA_ID:
                return toStringType(event.getCameraId());
            case CHANNEL_EVENT_SUBTYPE:
                return event.getSubTypeDescription().map(d -> toStringType(d)).orElse(UnDefType.NULL);
            case CHANNEL_EVENT_SNAPSHOT:
                return toRawType(event.getSnapshotUrl());
            case CHANNEL_EVENT_SNAPSHOT_URL:
                return toStringType(event.getSnapshotUrl());
        }
        if (event instanceof HomeEvent) {
            HomeEvent homeEvent = (HomeEvent) event;
            switch (channelId) {
                case CHANNEL_EVENT_VIDEO_STATUS:
                    return homeEvent.getVideoId() != null ? toStringType(homeEvent.getVideoStatus()) : UnDefType.NULL;
                case CHANNEL_EVENT_VIDEO_LOCAL_URL:
                    return getStreamURL(true, homeEvent.getVideoId(), homeEvent.getVideoStatus());
                case CHANNEL_EVENT_VIDEO_VPN_URL:
                    return getStreamURL(false, homeEvent.getVideoId(), homeEvent.getVideoStatus());
            }
        }
        return null;
    }

    private State getStreamURL(boolean local, @Nullable String videoId, VideoStatus videoStatus) {
        String url = local ? localUrl : vpnUrl;
        if ((local && !isLocal) || url == null || videoId == null || videoStatus != VideoStatus.AVAILABLE) {
            return UnDefType.NULL;
        }
        return toStringType("%s/vod/%s/index.m3u8", url, videoId);
    }
}
