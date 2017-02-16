package com.hotb.pgmacdesign.californiaprototype.listeners;

/**
 * Created by pmacdowell on 2017-02-13.
 */
public interface CustomClickCallbackLink {
    /**
     * For sending data between the event where this is clicked and the activity / fragment
     * @param object Object
     * @param customTag Object(s) being sent back. if you want, you can use if(obj instanceof XX)
     *                  and use that as a separator.
     * @param positionIfAvailable int position. This will be sent back as not null when there is
     *                            a reason to include it. That reason may be that the item selected
     *                            was chosen from a listview/ Recyclerview, in which case the
     *                            position can be helpful. If this is not set, it will send back
     *                            null instead.
     */
    public void itemClicked(Object object, Integer customTag, Integer positionIfAvailable);
}
