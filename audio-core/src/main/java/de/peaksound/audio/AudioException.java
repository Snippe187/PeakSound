package de.peaksound.audio;

/** Datei ist kein lesbares, unterstuetztes Audio oder verletzt ein Limit. */
public class AudioException extends Exception {
    public AudioException(String message) { super(message); }

    public AudioException(String message, Throwable cause) { super(message, cause); }
}
