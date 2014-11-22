package java.util.stream;

import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;

public interface Stream<T> {
  public void forEach(Consumer<? super T> consumer);
  public Stream<T> filter(Predicate<? super T> predicate);
  public Stream<T> sorted();
  public <A> A[] toArray(IntFunction<A[]> generator);
  public <R, A> R collect(Collector<T, A, R> collector);
  public <U> Stream<U> map(Function<? super T, ? extends U> mapper);
  public Stream<T> distinct();
  public Stream<T> sorted(Comparator<? super T> comparator);
}
