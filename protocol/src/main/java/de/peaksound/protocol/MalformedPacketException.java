package de.peaksound.protocol;

/** Wird bei unlesbaren oder manipulierten Paketen geworfen. Der Server ignoriert solche Pakete. */
public class MalformedPacketException extends RuntimeException {
    public MalformedPacketException(String message) { super(message); }
}
