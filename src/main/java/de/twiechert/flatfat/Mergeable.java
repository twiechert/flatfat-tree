package de.twiechert.flatfat;

/**
 * @author Tayfun Wiechert <wiechert@campus.tu-berlin.de>
 */
public interface Mergeable<IN,OUT>  {

    Mergeable<IN, OUT> merge(Mergeable<IN, OUT> other) throws Exception;

    OUT get() throws Exception ;


    void add(IN value) throws Exception;


    boolean isEmpty();
}
