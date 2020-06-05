package com.ichi2.libanki;

import android.content.Context;
import android.content.res.Resources;

import com.ichi2.anki.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

@SuppressWarnings("PMD.ExcessiveMethodLength")
public class CardStats {

    public static String report(Context context, Card c, Collection col) {
        Calendar cal = Calendar.getInstance();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");

        Resources res = context.getResources();
        StringBuilder builder = new StringBuilder();
        builder.append("<html><body ><table><colgroup><col span=\"1\" style=\"width: 40%;\"></col><col span=\"1\" style=\"width: 60%;\"></col></colgroup><tr><td valign=\"top\">");
        builder.append(res.getString(R.string.card_details_question));
        builder.append("</td><td>");
        builder.append(c._getQA(false).get("q"));
        builder.append("</td></tr><tr><td valign=\"top\">");
        builder.append(res.getString(R.string.card_details_answer));
        builder.append("</td><td>");
        builder.append(Utils.stripHTML(c._getQA(false).get("a")));
        builder.append("</td></tr><tr><td valign=\"top\">");

        long next = 0;
        if (c.getType() == 1 || c.getType() == 2) {
            if (c.getODid() != 0 || c.getQueue() < 0) {
                next = 0;
            } else {
                if (c.getQueue() == 2 || c.getQueue() == 3) {
                    next = Utils.intTime(1000) + ((c.getDue() - col.getSched().getToday()) * 86400000);
                } else {
                    next = c.getDue();
                }
            }
            if (next != 0) {
                cal.setTimeInMillis(next);
                builder.append(res.getString(R.string.card_details_due));
                builder.append("</td><td>");
                builder.append(df.format(cal.getTime()));
                builder.append("</td></tr><tr><td valign=\"top\">");
            }

        }

//      builder.append("</td></tr><tr><td>");

////        builder.append(res.getString(R.string.card_details_interval));
//      builder.append("</td><td>");
////        if (mInterval == 0) {
////            builder.append("-");
////        } else {
////            builder.append(Utils.getReadableInterval(context, mInterval));
////        }


//      builder.append("</td></tr><tr><td>");
////        builder.append(res.getString(R.string.card_details_ease));
//      builder.append("</td><td>");
//      double ease = Math.round(mFactor * 100);
//      builder.append(ease / 100);

//      builder.append("</td></tr><tr><td>");
////        builder.append(res.getString(R.string.card_details_average_time));
//      builder.append("</td><td>");
////        if (mYesCount + mNoCount == 0) {
////            builder.append("-");
////        } else {
////            builder.append(Utils.doubleToTime(mAverageTime));
////        }

//      builder.append("</td></tr><tr><td>");
////        builder.append(res.getString(R.string.card_details_total_time));
//      builder.append("</td><td>");
////        builder.append(Utils.doubleToTime(mReviewTime));

//      builder.append("</td></tr><tr><td>");
////        builder.append(res.getString(R.string.card_details_yes_count));
//      builder.append("</td><td>");
////        builder.append(mYesCount);

//      builder.append("</td></tr><tr><td>");
////        builder.append(res.getString(R.string.card_details_no_count));
//      builder.append("</td><td>");
////        builder.append(mNoCount);

//      builder.append("</td></tr><tr><td>");
////        builder.append(res.getString(R.string.card_details_added));
//      builder.append("</td><td>");
////        builder.append(DateFormat.getDateFormat(context).format(
////                (long) (mCreated - mDeck.getUtcOffset()) * 1000l));

//      builder.append("</td></tr><tr><td>");
////        builder.append(res.getString(R.string.card_details_changed));
//      builder.append("</td><td>");
////        builder.append(DateFormat.getDateFormat(context).format(
////                (long) (mModified - mDeck.getUtcOffset()) * 1000l));

//      builder.append("</td></tr><tr><td>");
////        builder.append(res.getString(R.string.card_details_tags));
//      builder.append("</td><td>");
////        String tags = Arrays.toString(mDeck
////                .allUserTags("WHERE id = " + mFactId));
////        builder.append(tags.substring(1, tags.length() - 1));

//      builder.append("</td></tr><tr><td>");
////        builder.append(res.getString(R.string.card_details_model));
//      builder.append("</td><td>");
////        Model model = Model.getModel(mDeck, mCardModelId, false);
////        builder.append(model.getName());

//      builder.append("</td></tr><tr><td>");
////        builder.append(res.getString(R.string.card_details_card_model));
//      builder.append("</td><td>");
////        builder.append(model.getCardModel(mCardModelId).getName());
        builder.append("</table></html></body>");
        return builder.toString();
    }

}
