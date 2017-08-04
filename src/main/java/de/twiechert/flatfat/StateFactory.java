package de.twiechert.flatfat;


/**
 * @author Tayfun Wiechert <wiechert@campus.tu-berlin.de>
 */
public interface StateFactory<IN, ACC>  {

	Mergeable<IN, ACC> getState() throws Exception;


}
