package java.util.stream;

public interface Collector<T, A, R> {
  public R doCollect(Stream<T> stream);
}