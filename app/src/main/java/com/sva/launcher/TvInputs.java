package com.sva.launcher;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The TV's own inputs (HDMI ports and the antenna tuner), read from Android's TV Input Framework so they can sit on
 * the home screen next to the apps. On TVs whose remote has no input button, this is the quickest way to switch.
 *
 * Nothing here changes the TV: it only lists inputs and asks the system to show one. Sticks and boxes have no
 * inputs, so the list is simply empty there. Approach adapted from Free My TV (MIT).
 */
final class TvInputs {
    static final int HDMI = 1, TUNER = 2;
    /** Apps that show live TV on Fire OS televisions and other Android TVs, tried in this order. */
    static final String[] LIVE_TV_APPS = {
            "com.amazon.tv.livetv", "com.amazon.mediatek.livetv", "com.mediatek.wwtv.tvcenter",
            "com.google.android.tv", "com.tcl.tv", "com.sony.dtv.tvx"
    };

    private TvInputs() { }

    static final class Input {
        final String id, label;
        final int kind, number;
        final boolean connected;

        Input(String id, String label, int kind, int number, boolean connected) {
            this.id = id;
            this.label = label;
            this.kind = kind;
            this.number = number;
            this.connected = connected;
        }
    }

    /** HDMI ports in port order, then the tuner. Empty when the device has no TV inputs (sticks, boxes). */
    static List<Input> list(Context context) {
        List<Input> out = new ArrayList<>();
        TvInputManager manager;
        List<TvInputInfo> all;
        try {
            manager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
            if (manager == null) return out;
            all = manager.getTvInputList();
        } catch (RuntimeException unavailable) {
            return out;
        }
        if (all == null) return out;

        // A device that speaks HDMI-CEC appears as a child input of its port, named after the device.
        Map<String, String> cecNames = new HashMap<>();
        List<TvInputInfo> ports = new ArrayList<>();
        TvInputInfo tuner = null;
        for (TvInputInfo info : all) {
            try {
                if (isHidden(info, context)) continue;
                if (info.getType() == TvInputInfo.TYPE_HDMI) {
                    if (info.getParentId() == null) ports.add(info);
                    else cecNames.put(info.getParentId(), String.valueOf(info.loadLabel(context)).trim());
                } else if (info.getType() == TvInputInfo.TYPE_TUNER && tuner == null && !info.isPassthroughInput()) {
                    tuner = info;
                }
            } catch (RuntimeException ignored) { }
        }
        Collections.sort(ports, (a, b) -> a.getId().compareTo(b.getId()));
        int connectedPorts = 0;
        for (TvInputInfo info : ports) if (state(manager, info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) connectedPorts++;
        // Some TVs report every port as connected, plugged in or not: then the state means nothing.
        boolean stateUseless = bogusConnected(ports.size(), connectedPorts);
        List<Input> hdmi = new ArrayList<>();
        for (int i = 0; i < ports.size(); i++) {
            TvInputInfo info = ports.get(i);
            String systemLabel = safeLabel(info, context);
            int number = portNumber(systemLabel, i + 1);
            boolean connected = stateUseless || state(manager, info.getId()) == TvInputManager.INPUT_STATE_CONNECTED;
            String custom = customLabel(info, context);
            String label = hdmiLabel(custom, connected ? cecNames.get(info.getId()) : null, number);
            hdmi.add(new Input(info.getId(), label, HDMI, number, connected));
        }
        Collections.sort(hdmi, (a, b) -> Integer.compare(a.number, b.number));
        out.addAll(hdmi);
        if (tuner != null) out.add(new Input(tuner.getId(), "TDT", TUNER, 0, true));
        return out;
    }

    /** Sample inputs for screenshot tests on emulators, which have none. */
    static List<Input> samples() {
        List<Input> out = new ArrayList<>();
        out.add(new Input("demo/hdmi1", "Fire TV", HDMI, 1, true));
        out.add(new Input("demo/hdmi2", "HDMI 2", HDMI, 2, false));
        out.add(new Input("demo/tuner", "TDT", TUNER, 0, true));
        return out;
    }

    /**
     * Pure: true when a TV with more than one HDMI port says every one of them is connected, which
     * some sets do whether or not anything is plugged in (seen on Sony, per OmarchyTV and CleanYourTV).
     * A TV with all ports really in use loses only the dimming of empty ports.
     */
    static boolean bogusConnected(int ports, int connected) {
        return ports > 1 && connected == ports;
    }

    /** Port number from the label the TV gives the port ("HDMI 2", "HDMI2 (ARC)"); otherwise its position. */
    static int portNumber(String systemLabel, int fallback) {
        if (systemLabel == null) return fallback;
        Matcher m = Pattern.compile("(?i)hdmi\\s*(\\d)").matcher(systemLabel);
        if (m.find()) return Integer.parseInt(m.group(1));
        m = Pattern.compile("(\\d)").matcher(systemLabel);
        return m.find() ? Integer.parseInt(m.group(1)) : fallback;
    }

    /** The name shown on the tile: one you gave it on the TV, else the connected device's own name, else "HDMI n". */
    static String hdmiLabel(String custom, String cecName, int number) {
        if (custom != null && !custom.trim().isEmpty()) return custom.trim();
        if (cecName != null && !cecName.trim().isEmpty() && !cecName.trim().toLowerCase(Locale.ROOT).startsWith("hdmi"))
            return cecName.trim();
        return "HDMI " + number;
    }

    /** Switches the TV to the input. Returns false when nothing on this device could show it. */
    static boolean open(Context context, Input input) {
        if (input.kind == HDMI && HdmiActivity.canShow(input.id)) {
            // Fire OS 8 televisions on MediaTek: show the input inside Sva (see HdmiActivity).
            try {
                context.startActivity(new Intent(context, HdmiActivity.class)
                        .putExtra(HdmiActivity.EXTRA_INPUT_ID, input.id)
                        .putExtra(HdmiActivity.EXTRA_LABEL, input.label)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            } catch (RuntimeException fallThrough) { }
        }
        // The channel MIME type too: some Live TV apps only answer the intent when it carries it.
        Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(input.kind == HDMI
                ? TvContract.buildChannelUriForPassthroughInput(input.id)
                : TvContract.buildChannelsUriForInput(input.id), input.kind == HDMI
                ? "vnd.android.cursor.item/channel" : "vnd.android.cursor.dir/channel")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(view);
            return true;
        } catch (ActivityNotFoundException | SecurityException notHandled) {
            // Fall back to the TV's live TV app, which at least opens the last input used.
            for (String pkg : LIVE_TV_APPS) {
                Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch == null) launch = context.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                if (launch == null) continue;
                try {
                    launch.setDataAndType(view.getData(), view.getType());
                    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return true;
                } catch (RuntimeException ignored) { }
            }
            return false;
        }
    }

    private static boolean isHidden(TvInputInfo info, Context context) {
        try { return info.isHidden(context); } catch (RuntimeException e) { return false; }
    }

    private static String safeLabel(TvInputInfo info, Context context) {
        try { return String.valueOf(info.loadLabel(context)); } catch (RuntimeException e) { return null; }
    }

    private static String customLabel(TvInputInfo info, Context context) {
        try {
            CharSequence custom = info.loadCustomLabel(context);
            return custom == null ? null : custom.toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int state(TvInputManager manager, String id) {
        try { return manager.getInputState(id); } catch (RuntimeException e) { return TvInputManager.INPUT_STATE_DISCONNECTED; }
    }
}
