package java.io;

public class UncheckedIOException extends RuntimeException {
  private static final long serialVersionUID = -5719611140800825697L;

  public UncheckedIOException(IOException cause) {
    super(cause);
  }

}
