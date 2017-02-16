package com.hotb.pgmacdesign.californiaprototype.listeners;

/**
 * This class serves as an interface link between long click events and an activity/ fragment.
 * Created by pmacdowell on 2017-02-13.
 */
public interface CustomLongClickCallbackLink {
    /**
     * For sending data between the event where this is long clicked and the activity / fragment
     * @param object Object(s) being sent back. if you want, you can use if(obj instanceof XX)
     *                  and use that as a separator.
     * @param customTag The integer custom tag. Use this for sending back specific tags that you
     *                  want to reference and differentiate between.
     * @param positionIfAvailable int position. This will be sent back as not null when there is
     *                            a reason to include it. That reason may be that the item selected
     *                            was chosen from a listview/ Recyclerview, in which case the
     *                            position can be helpful. If this is not set, it will send back
     *                            null instead.
     */
    public void itemLongClicked(Object object, Integer customTag, Integer positionIfAvailable);
}
