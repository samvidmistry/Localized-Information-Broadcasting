package `in`.samvidinfotech.beacondemo

import `in`.samvidinfotech.beacondemo.events.ToastEvent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.support.v4.app.ActivityCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import org.greenrobot.eventbus.EventBus

class HistoryAdapter(context: Context, data: List<LocationData>) : ArrayAdapter<LocationData>(context,
    R.layout.location_history_item, data) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View? {
        val item = getItem(position) ?: return convertView

        val layoutItem = if (convertView == null) {
            val view = LayoutInflater.from(parent.context).inflate(
                R.layout.location_history_item, parent,
                false
            )
            view
        } else {
            convertView
        }

        layoutItem.findViewById<TextView>(R.id.title_history).text = item.title
        layoutItem.findViewById<TextView>(R.id.content_history).text = item.content
        layoutItem.setOnClickListener {
            val uri = Uri.parse(String.format("geo:0,0?q=%f,%f(%s)", item.latitude ?: 0f, item.longitude ?: 0f,
                item.title))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.`package` = "com.google.android.apps.maps"
            if (intent.resolveActivity(parent.context.packageManager) != null) {
                ActivityCompat.startActivity(parent.context, intent, null)
            } else {
                EventBus.getDefault().post(ToastEvent("Google Maps not found on device"))
            }
        }
        return layoutItem
    }
}