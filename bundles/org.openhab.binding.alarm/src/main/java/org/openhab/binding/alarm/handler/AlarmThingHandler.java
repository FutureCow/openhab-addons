/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.alarm.handler;

import static org.openhab.binding.alarm.AlarmBindingConstants.*;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.binding.alarm.internal.AlarmController;
import org.openhab.binding.alarm.internal.AlarmException;
import org.openhab.binding.alarm.internal.AlarmListener;
import org.openhab.binding.alarm.internal.config.AlarmControllerConfig;
import org.openhab.binding.alarm.internal.config.AlarmZoneConfig;
import org.openhab.binding.alarm.internal.model.AlarmCommand;
import org.openhab.binding.alarm.internal.model.AlarmStatus;
import org.openhab.binding.alarm.internal.model.AlarmZone;
import org.openhab.binding.alarm.internal.model.AlarmZoneType;
import org.openhab.binding.alarm.internal.utils.StringUtils;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link AlarmThingHandler} is responsible for handling commands and updates for the alarm controller.
 *
 * @author Gerhard Riegler - Initial contribution
 */
public class AlarmThingHandler extends BaseThingHandler implements AlarmListener {
    private final Logger logger = LoggerFactory.getLogger(AlarmThingHandler.class);
    private AlarmController alarm;
    private AlarmControllerConfig controllerConfig;

    public AlarmThingHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        controllerConfig = getConfigAs(AlarmControllerConfig.class);
        alarm = new AlarmController(controllerConfig, this);

        ThingBuilder thingBuilder = editThing();
        ChannelTypeUID channelTypeUid = new ChannelTypeUID(BINDING_ID, CHANNEL_TYPE_ID_ALARMZONE);

        logger.info("Initializing alarm controller '{}' with {} alarm zones", getThing().getUID().getId(),
                controllerConfig.getAlarmZones());
        try {
            for (int zoneNumber = 1; zoneNumber <= controllerConfig.getAlarmZones(); zoneNumber++) {
                Channel channel = getThing().getChannel(CHANNEL_ID_ALARMZONE + zoneNumber);
                if (channel == null) {
                    Map<String, Object> properties = new HashMap<>();
                    properties.put("type", AlarmZoneType.ACTIVE.toString());
                    Configuration config = new Configuration(properties);
                    ChannelUID channelUid = getAlarmzoneChannelUID(zoneNumber);
                    channel = ChannelBuilder.create(channelUid, "Switch").withLabel("Alarm zone " + zoneNumber)
                            .withType(channelTypeUid).withConfiguration(config).build();
                    thingBuilder.withChannel(channel);
                }
                AlarmZoneConfig alarmZoneConfig = channel.getConfiguration().as(AlarmZoneConfig.class);
                AlarmZone alarmZone = new AlarmZone(channel.getUID().getId(), alarmZoneConfig);
                alarm.addAlarmZone(alarmZone);
            }

            for (Channel channel : getThing().getChannels()) {
                if (isAlarmZone(channel.getUID())) {
                    int zoneNumber = getAlarmZoneNumber(channel.getUID());
                    if (zoneNumber > controllerConfig.getAlarmZones()) {
                        thingBuilder.withoutChannel(channel.getUID()); // remove channel
                    }
                }
            }

            updateThing(thingBuilder.build());

            updateStatus(ThingStatus.ONLINE);
        } catch (AlarmException | NumberFormatException ex) {
            logger.error("{}", ex.getMessage());
            updateStatus(ThingStatus.OFFLINE);
        }
    }

    @Override
    public void dispose() {
        alarm.dispose();
        alarm = null;
    }

    @Override
    public void channelLinked(ChannelUID channelUID) {
        if (isAlarmZone(channelUID)) {
            try {
                AlarmZone alarmZone = alarm.getAlarmZone(channelUID.getId());
                updateStateIfLinked(channelUID.getId(),
                        alarmZone.isClosed() ? OpenClosedType.CLOSED : OpenClosedType.OPEN);
            } catch (AlarmException ex) {
                logger.warn("{}", ex.getMessage());
            }
        } else {
            switch (channelUID.getId()) {
                case CHANNEL_ID_STATUS:
                    alarmStatusChanged(alarm.getStatus());
                    break;
                case CHANNEL_ID_COUNTDOWN:
                    alarmCountdownChanged(0);
                    break;
                case CHANNEL_ID_INTERNAL_ARMING_POSSIBLE:
                    readyToArmInternallyChanged(alarm.isReadyToArmInternally());
                    break;
                case CHANNEL_ID_EXTERNAL_ARMING_POSSIBLE:
                    readyToArmExternallyChanged(alarm.isReadyToArmExternally());
                    break;
                case CHANNEL_ID_PASSTHROUGH_POSSIBLE:
                    readyToPassthroughChanged(alarm.isReadyToPassthrough());
                    break;
            }
        }
    }

    @Override
    public void handleCommand(@NonNull ChannelUID channelUID, @NonNull Command command) {
        if (channelUID.getId().equals(CHANNEL_ID_COMMAND)) {
            logger.debug("handleCommand: Alarmzone {} received command {}", channelUID.getId(), command);
            try {
                alarm.doCommand(AlarmCommand.parse(command.toString()));
            } catch (AlarmException ex) {
                logger.warn("{}", ex.getMessage());
            }
        } else if (isAlarmZone(channelUID)) {
            try {
                boolean isClosed = false;
                if (command instanceof OpenClosedType) {
                    isClosed = command == OpenClosedType.CLOSED;
                } else if (command instanceof OnOffType) {
                    isClosed = command == OnOffType.ON;
                } else if (command instanceof StringType) {
                    isClosed = !"OPEN".equalsIgnoreCase(((StringType) command).toFullString());
                } else if (command instanceof DecimalType) {
                    isClosed = ((DecimalType) command).longValue() != 0;
                } else {
                    throw new AlarmException("Unsupported type " + command.getClass().getSimpleName()
                            + ", only Switch, String and Number supported");
                }
                logger.debug("Alarmzone {} received state {}, zone set to {}", channelUID.getId(), command,
                        isClosed ? OpenClosedType.CLOSED : OpenClosedType.OPEN);
                alarm.alarmZoneChanged(channelUID.getId(), isClosed);
            } catch (AlarmException ex) {
                logger.warn("{}", ex.getMessage());
            }
        }
    }

    @Override
    public void alarmStatusChanged(AlarmStatus status) {
        updateStateIfLinked(CHANNEL_ID_STATUS, new StringType(status.toString()));
    }

    @Override
    public void alarmCountdownChanged(int value) {
        updateStateIfLinked(CHANNEL_ID_COUNTDOWN, new DecimalType(value));
    }

    @Override
    public void readyToArmInternallyChanged(boolean isReady) {
        updateStateIfLinked(CHANNEL_ID_INTERNAL_ARMING_POSSIBLE, isReady ? OnOffType.ON : OnOffType.OFF);
    }

    @Override
    public void readyToArmExternallyChanged(boolean isReady) {
        updateStateIfLinked(CHANNEL_ID_EXTERNAL_ARMING_POSSIBLE, isReady ? OnOffType.ON : OnOffType.OFF);
    }

    @Override
    public void readyToPassthroughChanged(boolean isReady) {
        updateStateIfLinked(CHANNEL_ID_PASSTHROUGH_POSSIBLE, isReady ? OnOffType.ON : OnOffType.OFF);
    }

    private void updateStateIfLinked(String channelId, State state) {
        Channel channel = getThing().getChannel(channelId);
        if (channel != null) {
            if (isLinked(channel.getUID())) {
                updateState(channel.getUID(), state);
            }
        } else {
            logger.error("Channel with id '{}' not available", channelId);
        }
    }

    private boolean isAlarmZone(ChannelUID channelUID) {
        return channelUID.getId().startsWith(CHANNEL_ID_ALARMZONE);
    }

    private int getAlarmZoneNumber(ChannelUID channelUID) {
        return Integer.parseInt(StringUtils.substringAfter(channelUID.getId(), "_"));
    }

    private ChannelUID getAlarmzoneChannelUID(int zoneNumber) {
        return new ChannelUID(String.format("%s:%s:%s:%s", BINDING_ID, CONTROLLER, getThing().getUID().getId(),
                CHANNEL_ID_ALARMZONE + zoneNumber));
    }
}
