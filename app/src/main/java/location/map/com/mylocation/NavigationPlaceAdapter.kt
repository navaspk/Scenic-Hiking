package location.map.com.mylocation

import android.content.Context
import android.location.Geocoder
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import com.mapbox.geojson.Point
import java.util.*

class NavigationPlaceAdapter(var mContext: Context, var mPlaceFromNavigate: ArrayList<Point>)
        : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private lateinit var mLayoutInflater: LayoutInflater
    //var mPlaceFromNavigate: Point? = null


    override fun getItemCount(): Int {
        if (mPlaceFromNavigate != null && mPlaceFromNavigate.isNotEmpty())
            return mPlaceFromNavigate.size
        return 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        mLayoutInflater = LayoutInflater.from(mContext)
        return ProductItemViewHolder(mLayoutInflater.inflate
            (R.layout.layout_child_item, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // load the address name in text view
        bindContent(holder, mPlaceFromNavigate[position]!!)
        // move the map to respective place when clicking on item
    }

    /**
     * To bind the content to view
     */
    private fun bindContent(holder: RecyclerView.ViewHolder, destinationPoint: Point) {
        /**
         * Getting address from the list
         */
        val geoCoder = Geocoder(mContext, Locale.getDefault())
        val addresses = geoCoder.getFromLocation(
            destinationPoint.latitude(),
            destinationPoint.longitude(),
            1)
        val obj = addresses[0]
        val add = obj.getAddressLine(0)
        ((holder) as ProductItemViewHolder).placeName.text = add

        ((holder) as ProductItemViewHolder).container.setOnClickListener {
            (mContext as MapActivity).moveCamara(destinationPoint.latitude(), destinationPoint.longitude())
        }
    }

    fun setContent(list : Point) {
        mPlaceFromNavigate.add(list)
    }

    /**
     * holder class for the each item
     */
    inner class ProductItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var placeName = view.findViewById(R.id.place_name) as TextView
        val container = view.findViewById(R.id.container) as RelativeLayout
    }
}