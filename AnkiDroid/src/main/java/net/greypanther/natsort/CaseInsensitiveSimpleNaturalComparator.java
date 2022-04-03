package net.greypanther.natsort;

import java.util.Comparator;

/**
 * The same as {@link SimpleNaturalComparator} but comparison is done after converting each
 * character to lower case.
 */
public final class CaseInsensitiveSimpleNaturalComparator<T extends CharSequence>
    extends AbstractSimpleNaturalComparator<T> implements Comparator<T> {
  @SuppressWarnings("rawtypes")
  private static final Comparator INSTANCE = new CaseInsensitiveSimpleNaturalComparator();

  private CaseInsensitiveSimpleNaturalComparator() {
    // to be instantiated only internally
  }

  @Override
  int compareChars(char c1, char c2) {
    return Character.toLowerCase(c1) - Character.toLowerCase(c2);
  }

  @SuppressWarnings("unchecked")
  public static <T extends CharSequence> Comparator<T> getInstance() {
    return INSTANCE;
  }
}
