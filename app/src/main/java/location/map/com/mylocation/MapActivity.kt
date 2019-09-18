package location.map.com.mylocation

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.TextUtils
import android.widget.Toast
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAllowOverlap
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconIgnorePlacement
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.DirectionsRoute
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import com.google.gson.Gson
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher
import kotlin.collections.ArrayList


class MapActivity : AppCompatActivity(), OnMapReadyCallback,
    MapboxMap.OnMapLongClickListener, PermissionsListener {
    private var mMapView: MapView? = null
    private var mMapboxMap: MapboxMap? = null
    private var mPermissionsManager: PermissionsManager? = null
    private var mLocationComponent: LocationComponent? = null
    private var mCurrentRoute: DirectionsRoute? = null
    private var mNavigationMapRoute: NavigationMapRoute? = null
    private var mButton: Button? = null
    private var mDestinationPoint : Point? = null
    private var mRecyclerView : RecyclerView? = null
    private var mAdapter : NavigationPlaceAdapter? = null
    private var mLinearLayout : LinearLayout? = null
    private var mBottomSheetBehavior: BottomSheetBehavior<*>? = null

    private var mOriginal : Point? = null

    companion object {
        val STORE_FILE_NAME = "store_file"
        private val TAG = "MapActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(this, getString(R.string.map_access_token))
        setContentView(R.layout.activity_map)
        mLinearLayout = findViewById(R.id.bottom_sheet)
        mBottomSheetBehavior = BottomSheetBehavior.from(mLinearLayout)
        mBottomSheetBehavior?.setHideable(false)
        mBottomSheetBehavior?.peekHeight = 100
        mRecyclerView = findViewById(R.id.recycler_view)
        val placeList = getDataFromSharedPreferences()

        mRecyclerView?.setNestedScrollingEnabled(false)
        mRecyclerView?.setHasFixedSize(true)
        val layoutManager = LinearLayoutManager(this)
        mRecyclerView?.setLayoutManager(layoutManager)
        mAdapter = NavigationPlaceAdapter(this, placeList)
        mRecyclerView?.setAdapter(mAdapter)

        mMapView = findViewById(R.id.mapView)
        mMapView!!.onCreate(savedInstanceState)
        mMapView!!.getMapAsync(this)
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        this.mMapboxMap = mapboxMap
        mapboxMap.setStyle(getString(R.string.navigation_guidance_day)) { style ->
            enableLocationComponent(style)

            addDestinationIconSymbolLayer(style)

            mapboxMap.addOnMapLongClickListener(this@MapActivity)
            mButton = findViewById(R.id.startButton)
            mButton!!.setOnClickListener {
                // when selecting navigation, we are adding item to shared preference
                // for populating as separate list
                if (mDestinationPoint != null) {
                    setDataFromSharedPreferences(mDestinationPoint!!)
                    // once navigation started, the moment we are updating view with recent navigation
                    mAdapter?.setContent(mDestinationPoint!!)
                    mAdapter?.notifyDataSetChanged()
                }

                val simulateRoute = true
                if (mCurrentRoute != null) {
                    val options = NavigationLauncherOptions.builder()
                        .directionsRoute(mCurrentRoute)
                        .shouldSimulateRoute(simulateRoute)
                        .build()
                    // Call this method with Context from within an Activity
                    NavigationLauncher.startNavigation(this@MapActivity, options)
                }
            }
        }
    }

    private fun addDestinationIconSymbolLayer(loadedMapStyle: Style) {
        loadedMapStyle.addImage(
            "destination-icon-id",
            BitmapFactory.decodeResource(this.resources, R.drawable.mapbox_marker_icon_default)
        )
        val geoJsonSource = GeoJsonSource("destination-source-id")
        loadedMapStyle.addSource(geoJsonSource)
        val destinationSymbolLayer = SymbolLayer("destination-symbol-layer-id", "destination-source-id")
        destinationSymbolLayer.withProperties(
            iconImage("destination-icon-id"),
            iconAllowOverlap(true),
            iconIgnorePlacement(true)
        )
        loadedMapStyle.addLayer(destinationSymbolLayer)
    }

    override fun onMapLongClick(point: LatLng): Boolean {
        val destinationPoint = Point.fromLngLat(point.longitude, point.latitude)
        val originPoint = Point.fromLngLat(
            mLocationComponent?.lastKnownLocation!!.longitude,
            mLocationComponent?.lastKnownLocation!!.latitude
        )

        mDestinationPoint = destinationPoint
        mOriginal = originPoint

        val source = mMapboxMap?.style?.getSourceAs<GeoJsonSource>("destination-source-id")
        source?.setGeoJson(Feature.fromGeometry(destinationPoint))

        getRoute(originPoint, destinationPoint)
        mButton?.isEnabled = true
        mButton?.setBackgroundResource(R.color.blue)
        return true
    }

    private fun getRoute(origin: Point, destination: Point) {
        NavigationRoute.builder(this)
            .accessToken(Mapbox.getAccessToken()!!)
            .origin(origin)
            .destination(destination)
            .build()
            .getRoute(object : Callback<DirectionsResponse> {
                override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                    Log.d(TAG, "Response " + response.code())
                    if (response.body() == null) {
                        Log.e(TAG, "No routes found, make sure you set the right user and access token.")
                        return
                    } else if (response.body()!!.routes().size < 1) {
                        Log.e(TAG, "No routes found")
                        return
                    }

                    mCurrentRoute = response.body()!!.routes()[0]

                    // Draw the route on the map
                    if (mNavigationMapRoute != null) {
                        mNavigationMapRoute?.removeRoute()
                    } else {
                        mNavigationMapRoute =
                                NavigationMapRoute(null, mMapView!!, mMapboxMap!!, R.style.NavigationMapRoute)
                    }
                    mNavigationMapRoute?.addRoute(mCurrentRoute)
                }

                override fun onFailure(call: Call<DirectionsResponse>, throwable: Throwable) {
                    Log.e(TAG, "Error: " + throwable.message)
                }
            })
    }

    @SuppressLint("MissingPermission")
    private fun enableLocationComponent(loadedMapStyle: Style) {
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            mLocationComponent = mMapboxMap?.locationComponent
            mLocationComponent?.activateLocationComponent(this, loadedMapStyle)
            mLocationComponent?.isLocationComponentEnabled = true
            // Set the component's camera mode
            mLocationComponent?.cameraMode = CameraMode.TRACKING
            // setting render mode to denote the arrow pointer
            mLocationComponent?.renderMode = RenderMode.COMPASS
        } else {
            mPermissionsManager = PermissionsManager(this)
            mPermissionsManager?.requestLocationPermissions(this)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        mPermissionsManager?.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onExplanationNeeded(permissionsToExplain: List<String>) {
        Toast.makeText(this, R.string.user_location_permission_explanation, Toast.LENGTH_LONG).show()
    }

    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            enableLocationComponent(mMapboxMap?.style!!)
        } else {
            Toast.makeText(this, R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        mMapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mMapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mMapView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        mMapView?.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mMapView?.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        mMapView?.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mMapView?.onLowMemory()
    }

    private fun setDataFromSharedPreferences(curProduct: Point) {
        val gson = Gson()
        val jsonCurProduct = gson.toJson(curProduct)

        val sharedPref = Mapbox.getApplicationContext().getSharedPreferences(STORE_FILE_NAME,
            Context.MODE_PRIVATE)
        var isPushed = false
        val editor = sharedPref.edit()
        //checking any json availability
        for (i in 1..3) {
            var key = "KEY_PREFS"+i
            val json = sharedPref.getString(key, "")
            if (!TextUtils.isEmpty(json)) {
                continue
            } else {
                isPushed = true
                editor.putString("KEY_PREFS"+i, jsonCurProduct)
                editor.commit()
                return
            }
        }

        // to update the latest marker, which will override the existing one
        if (!isPushed)
            editor.putString("KEY_PREFS1", jsonCurProduct)
        editor.commit()
    }

    private fun getDataFromSharedPreferences(): ArrayList<Point> {
        val gson = Gson()
        var jsonArray = ArrayList<Point>(3)
        val sharedPref = Mapbox.getApplicationContext().getSharedPreferences(STORE_FILE_NAME,
            Context.MODE_PRIVATE)

        for (i in 1..3) {
            val json = sharedPref.getString("KEY_PREFS"+i, "")
            if (!TextUtils.isEmpty(json)) {
                val point = gson.fromJson<Point>(json, Point::class.java)
                jsonArray.add(point)
                continue
            } else {
                break;
            }
        }

        return jsonArray
    }

    fun moveCamara(lati : Double, longi : Double) {
        mMapboxMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lati, longi), 10.0))
    }

    /*fun mergeJSONObjects(json1 : JSONObject, json2 : JSONObject) : JSONObject {
		var mergedJSON = JSONObject()
		try {
            mergedJSON = JSONObject(json1, JSONObject.names(json1));
			for (key in JSONObject.getNames(json2)) {
				mergedJSON.put(key, json2.get(key))
			}

		} catch (e : JSONException) {
			throw RuntimeException("JSON Exception= $e");
		}
		return mergedJSON;
	}*/
}
