package io.github.vulpesstella.iaspis.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import net.minecraft.util.StringUtils;

final class TargetNarrator {
    private static final long DELAY = 350000000L;
    private static final String WINDOWS_SPEAK =
            "$ErrorActionPreference='Stop'; Add-Type -AssemblyName System.Speech; "
            + "[Console]::InputEncoding=[Text.Encoding]::UTF8; "
            + "$voice=New-Object System.Speech.Synthesis.SpeechSynthesizer; "
            + "try { $voice.Speak([Console]::In.ReadToEnd()) } finally { $voice.Dispose() }";

    private final String[][] commands;
    private String target;
    private long since;
    private boolean requested;
    private int generation;
    private Process active;
    private boolean failed;
    private boolean warn;

    TargetNarrator() {
        this(commandsFor(System.getProperty("os.name", "")));
    }

    TargetNarrator(String[][] commands) {
        this.commands = commands;
    }

    synchronized void update(String headline) {
        String text = headline == null ? null : StringUtils.stripControlCodes(headline).trim();
        if (text != null && text.isEmpty()) text = null;
        long now = System.nanoTime();
        if (target == null ? text != null : !target.equals(text)) {
            target = text;
            since = now;
            requested = false;
            generation++;
            if (active != null) {
                active.destroy();
                active = null;
            }
        }
        if (target == null || requested || failed || now - since < DELAY) return;
        requested = true;
        final int job = generation;
        final String phrase = target.substring(0, Math.min(target.length(), 256));
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() { speak(job, phrase); }
        }, "Iaspis narrator");
        worker.setDaemon(true);
        worker.start();
    }

    synchronized boolean takeWarning() {
        boolean result = warn;
        warn = false;
        return result;
    }

    private void speak(int job, String phrase) {
        Process process = null;
        try {
            process = start();
            synchronized (this) {
                if (job != generation) return;
                active = process;
            }
            OutputStream input = process.getOutputStream();
            try {
                input.write(phrase.getBytes(StandardCharsets.UTF_8));
            } finally {
                input.close();
            }
            InputStream output = process.getInputStream();
            try {
                byte[] buffer = new byte[256];
                while (output.read(buffer) != -1) {
                    // Discard console output from the speech engine.
                }
            } finally {
                output.close();
            }
            if (process.waitFor() != 0) unavailable(job);
        } catch (IOException e) {
            unavailable(job);
        } catch (SecurityException e) {
            unavailable(job);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (process != null) process.destroy();
            synchronized (this) {
                if (active == process) active = null;
            }
        }
    }

    private Process start() throws IOException {
        for (String[] command : commands) {
            try {
                return new ProcessBuilder(command).redirectErrorStream(true).start();
            } catch (IOException e) {
                // Try the next installed speech engine.
            }
        }
        throw new IOException("No speech engine could start");
    }

    private synchronized void unavailable(int job) {
        if (job == generation) {
            failed = true;
            warn = true;
        }
    }

    private static String[][] commandsFor(String osName) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            return new String[][] { { "powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                    "-WindowStyle", "Hidden", "-Command", WINDOWS_SPEAK } };
        }
        if (os.startsWith("linux")) {
            return new String[][] { { "espeak-ng", "--stdin" }, { "espeak", "--stdin" } };
        }
        if (os.startsWith("mac")) {
            return new String[][] { { "/usr/bin/say", "-f", "-" } };
        }
        return new String[0][];
    }
}
