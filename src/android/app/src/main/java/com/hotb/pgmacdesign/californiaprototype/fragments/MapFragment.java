package com.hotb.pgmacdesign.californiaprototype.fragments;

import android.content.pm.PackageManager;
import android.graphics.Point;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SearchView;

import com.daimajia.androidanimations.library.Techniques;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.hotb.pgmacdesign.californiaprototype.R;
import com.hotb.pgmacdesign.californiaprototype.customui.ScaleBar;
import com.hotb.pgmacdesign.californiaprototype.listeners.CustomFragmentListener;
import com.hotb.pgmacdesign.californiaprototype.listeners.MyLocationListener;
import com.hotb.pgmacdesign.californiaprototype.listeners.OnTaskCompleteListener;
import com.hotb.pgmacdesign.californiaprototype.mapzen.MapzenAPICalls;
import com.hotb.pgmacdesign.californiaprototype.mapzen.MapzenPOJO;
import com.hotb.pgmacdesign.californiaprototype.mapzen.MapzenSimpleObject;
import com.hotb.pgmacdesign.californiaprototype.misc.Constants;
import com.hotb.pgmacdesign.californiaprototype.misc.L;
import com.hotb.pgmacdesign.californiaprototype.misc.MyApplication;
import com.hotb.pgmacdesign.californiaprototype.utilities.AnimationUtilities;
import com.hotb.pgmacdesign.californiaprototype.utilities.DisplayManagerUtilities;
import com.hotb.pgmacdesign.californiaprototype.utilities.FragmentUtilities;
import com.hotb.pgmacdesign.californiaprototype.utilities.LocationUtilities;
import com.hotb.pgmacdesign.californiaprototype.utilities.MiscUtilities;
import com.hotb.pgmacdesign.californiaprototype.utilities.PermissionUtilities;
import com.hotb.pgmacdesign.californiaprototype.utilities.StringUtilities;
import com.hotb.pgmacdesign.californiaprototype.utilities.ThreadUtilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static android.support.v4.content.PermissionChecker.PERMISSION_GRANTED;

/**
 * Created by pmacdowell on 2017-02-14.
 */

public class MapFragment extends Fragment implements OnMapReadyCallback,
        GoogleMap.OnMyLocationButtonClickListener, MyLocationListener.LocationLoadedListener,
        SearchView.OnQueryTextListener, OnTaskCompleteListener, AdapterView.OnItemClickListener,
        View.OnFocusChangeListener, GoogleMap.OnMapLongClickListener, GoogleMap.OnCircleClickListener, GoogleMap.OnCameraMoveListener, Handler.Callback {

    //Tag
    public final static String TAG = "MapFragment";
    private static final double DEFAULT_RADIUS_METERS = 1000000;
    private static final double RADIUS_OF_EARTH_METERS = 6371009;
    private static final String DISMISS_SCALE_BAR = "dismiss_scale_bar";

    //Map Objects
    private GoogleMap googleMap;
    private Circle lastDrawnCircle;
    private Location location;
    private List<MapzenSimpleObject> searchResultsList;
    private List<String> searchResultsToShow;
    private MapzenPOJO lastMapQueryData;

    //UI
    private SearchView searchView;
    private ListView fragment_map_search_listview;
    private RelativeLayout fragment_map_main_layout;
    private ScaleBar mScaleBar;

    //Adapters
    private ArrayAdapter adapter;

    //Variables
    private int DEFAULT_ZOOM_LEVEL = 15;
    private boolean mapHasLoaded, locationIsEnabled;
    private PermissionUtilities.permissionsEnum locPerm = PermissionUtilities
            .permissionsEnum.ACCESS_FINE_LOCATION;

    //Misc
    private Timer timer, scaleBarTimer;
    private String query;
    private boolean callInProgress, secondaryCall;
    private MapzenAPICalls api;
    private Handler handler;
    private DisplayManagerUtilities dmu;

    //Gap so that it doesn't query every single time they type a letter
    private static final long TYPING_GAP = ((int)(Constants.ONE_SECOND * 0.35));
    private static final int NUM_SECONDS_ON_SCALEBAR_HIDE = 2;

    //Listeners
    private MyLocationListener locationListener;

    //Empty constructor
    public MapFragment() {}

    //Instance
    public static MapFragment newInstance() {
        MapFragment fragment = new MapFragment();
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mapHasLoaded = false;
        this.locationIsEnabled = true;
        this.api = new MapzenAPICalls(getActivity(), this);
        this.callInProgress = false;
        this.secondaryCall = false;
        this.handler = ThreadUtilities.getHandlerWithCallback(this);
        this.dmu = new DisplayManagerUtilities(getActivity());
        //Utilize instanceState here
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);
        initVariables();
        initUi(view);
        return view;
    }

    private void initVariables(){
        locationListener = new MyLocationListener(MyApplication.getInstance(), this);
    }

    private void initUi(View view) {
        setupMap();
        this.searchView = (SearchView) view.findViewById(
                R.id.fragment_map_searchview);
        this.searchView.setIconified(false);
        this.searchView.clearFocus();
        this.searchView.setOnQueryTextListener(this);
        this.searchView.setOnFocusChangeListener(this);
        this.fragment_map_search_listview = (ListView) view.findViewById(
                R.id.fragment_map_search_listview);
        this.fragment_map_search_listview.setOnItemClickListener(this);
        this.fragment_map_main_layout = (RelativeLayout) view.findViewById(
                R.id.fragment_map_main_layout);
    }

    private void setupMap(){
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.fragment_map_map);
        mapFragment.getMapAsync(this);
    }


    /**
     * Switch to a different fragment
     * @param x
     */
    private void switchFragment(int x){
        FragmentUtilities.switchFragments(x, ((CustomFragmentListener)getActivity()));
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((CustomFragmentListener)getActivity()).setCurrentFragment(Constants.FRAGMENT_MAP);

    }

    /**
     * Move the camera to a specific location. To go to default (Los Angeles), pass in a
     * Longitude and Latitude of -1, -1.
     * @param latitude Latitude
     * @param longitude Longitude
     */
    private void moveCamera(double latitude, double longitude){
        if(latitude == -1){
            latitude = Constants.DEFAULT_LATITUDE;
        }
        if(longitude == -1){
            longitude = Constants.DEFAULT_LONGITUDE;
        }
        LatLng latLng = new LatLng(latitude, longitude);
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM_LEVEL);
        L.m("move camera to lat = " + latitude + " and lng = " + longitude);
        this.googleMap.animateCamera(cameraUpdate);
    }



    /**
     * Map loaded. Initialize it and make it ready to manipulate
     * @param aGoogleMap
     */
    @Override
    public void onMapReady(GoogleMap aGoogleMap) {
        this.googleMap = aGoogleMap;
        this.mapHasLoaded = true;
        this.enableScaleBar();
        this.googleMap.setOnCameraMoveListener(this);
        this.googleMap.setOnMapLongClickListener(this);
        this.googleMap.setIndoorEnabled(false);
        this.googleMap.getUiSettings().setCompassEnabled(true);
        this.googleMap.setContentDescription(getString(R.string.map_content_description));
        try {
            this.googleMap.setMyLocationEnabled(true);
        } catch (SecurityException se){
            se.printStackTrace();
            //locationError(getString(R.string.gps_must_be_enabled));
        }
        this.onMyLocationButtonClick();
        startLocationServices();

    }

    /**
     * Start location services. Check permission first, then make the loc call
     */
    private void startLocationServices(){
        if(checkPermission()){
            try {
                LocationUtilities.startListeningForLocation(getActivity(), locationListener);
            } catch (SecurityException se){
                //this would only ping if the user somehow managed to disable it milliseconds after enabling it
                se.printStackTrace();
            }
        }
    }

    /**
     * Simple hide or show listview
     * @param bool true to show, false to hide
     */
    private void showListview(boolean bool){
        if(bool){
            fragment_map_search_listview.setVisibility(View.VISIBLE);
        } else {
            fragment_map_search_listview.setVisibility(View.GONE);
        }
    }

    /**
     * Sets map results to null and hides the listview
     * @param mapData MapData retrieved from the API Call
     * {@link com.hotb.pgmacdesign.californiaprototype.mapzen.MapzenAPICalls}
     */
    private void setSearchResults(MapzenPOJO mapData){
        this.lastMapQueryData = null;
        if(mapData == null){
            showListview(false);
            return;
        } else {
            showListview(true);
        }

        this.searchResultsList = new ArrayList<>();
        this.searchResultsToShow = new ArrayList<>();
        this.lastMapQueryData = mapData;

        List<MapzenPOJO.MapzenFeatures> featuresList = this.lastMapQueryData.getFeatures();
        if(MiscUtilities.isListNullOrEmpty(featuresList)){
            String noResults = getString(R.string.no_search_results);
            this.searchResultsToShow.add(noResults);
            this.adapter = new ArrayAdapter<String>(getActivity(),
                    R.layout.simple_listview_layout, this.searchResultsToShow);
            this.fragment_map_search_listview.setAdapter(this.adapter);
            this.adapter.notifyDataSetChanged();

            return;
        }

        for(MapzenPOJO.MapzenFeatures features : featuresList){
            String str = null;
            MapzenSimpleObject simpleObject = new MapzenSimpleObject();

            MapzenPOJO.MapzenGeometry geometry = features.getGeometry();
            MapzenPOJO.MapzenProperties properties = features.getProperties();

            if(geometry != null){
                double[] coords = geometry.getCoordinates();
                if(coords != null){
                    if(coords.length == 2){
                        simpleObject.setLongitude(coords[0]);
                        simpleObject.setLatitude(coords[1]);
                    }
                }
            }
            if(properties != null){
                simpleObject.setId(properties.getId());
                simpleObject.setName(properties.getName());
                simpleObject.setCity(properties.getCity());
                simpleObject.setState(properties.getState());
                simpleObject.setCounty(properties.getCounty());
                simpleObject.setCountry(properties.getCountry());
                simpleObject.setSimpleLocation(properties.getLabel());
                simpleObject.setPostalcode(properties.getPostalcode());
                simpleObject.setNeighbourhood(properties.getNeighbourhood());
                simpleObject.setStateAbbreviation(properties.getStateAbbreviation());
                simpleObject.setDistanceFromEnteredLocation(properties.getDistance());

                if(!StringUtilities.isNullOrEmpty(properties.getPostalcode())){
                    String label = simpleObject.getSimpleLocation();
                    label = label + ", " + properties.getPostalcode();
                    simpleObject.setSimpleLocation(label);
                }
            }

            str = simpleObject.getSimpleLocation();
            if(StringUtilities.isNullOrEmpty(str)){
                String ss1 = simpleObject.getName();
                String ss2 = simpleObject.getCity();
                if(StringUtilities.isNullOrEmpty(ss1) && StringUtilities.isNullOrEmpty(ss2)){
                    str = getString(R.string.unknown_location);
                } else {
                    str = ss1 + ", " + ss2;
                }
            }

            this.searchResultsToShow.add(str);
            this.searchResultsList.add(simpleObject);
        }

        if(searchResultsList.size() == 0){
            this.setSearchResults(null);
            return;
        }

        this.adapter = new ArrayAdapter<String>(getActivity(),
                R.layout.simple_listview_layout, this.searchResultsToShow);
        this.fragment_map_search_listview.setAdapter(this.adapter);
        this.adapter.notifyDataSetChanged();


    }

    /**
     * Manage permission results
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        //super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == locPerm.getPermissionCode()){
            if(grantResults[0] == PERMISSION_GRANTED){
                startLocationServices();
            } else {
                locationError(getString(R.string.gps_must_be_enabled));
            }
        }
    }

    /**
     * Make a check for permissions
     * @return True if granted, false if not
     */
    private boolean checkPermission(){
        if(Build.VERSION.SDK_INT >= 23){
            if(ContextCompat.checkSelfPermission(getContext(),
                    locPerm.getPermissionManifestName()) != PackageManager.PERMISSION_GRANTED) {
                this.requestPermissions(new String[]{locPerm.getPermissionManifestName()},
                        locPerm.getPermissionCode());
                return false;
            } else {
                return true;
            }
        } else {
            return true;
        }
        /*
        return PermissionUtilities.PermissionsRequestShortcutReturn(getActivity(),
                new PermissionUtilities.permissionsEnum[]{
                        PermissionUtilities.permissionsEnum.ACCESS_FINE_LOCATION});
        */
    }

    @Override
    public boolean onMyLocationButtonClick() {
        if(!this.locationIsEnabled){
            L.Toast(getActivity(), getString(R.string.loading_last_known_loc));
        }
        moveCamera(MyApplication.getLastKnownLat(), MyApplication.getLastKnownLng());
        return false;
    }

    @Override
    public void locationTurnedOn(boolean bool) {
        this.locationIsEnabled = bool;
    }

    @Override
    public void locationLoaded(Location location) {
        if(location != null){
            this.location = location;
            try {
                this.googleMap.setMyLocationEnabled(true);
            } catch (SecurityException se){
                se.printStackTrace();
                locationError(getString(R.string.gps_must_be_enabled));
            }
        }
    }

    @Override
    public void locationError(String error) {
        if(!StringUtilities.isNullOrEmpty(error)){
            L.Toast(getActivity(), error);
        }
    }

    private void queryMaps(final String query){
        this.query = query;
        if(timer == null){
            timer = new Timer();
        }
        timer.cancel();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                makeSearchCall();
            }
        }, TYPING_GAP);
    }
    @Override
    public boolean onQueryTextSubmit(String query) {
        if(query != null){
            if(query.length() > 1){
                queryMaps(query);
            }
        }
        return false;
    }

    private void makeSearchCall(){
        if(callInProgress){
            return;
        }
        callInProgress = true;
        Double lastKnownLat = MyApplication.getLastKnownLat();
        Double lastKnownLng = MyApplication.getLastKnownLng();

        if(lastKnownLat == -1){
            lastKnownLat = null;
        }
        if(lastKnownLng == -1){
            lastKnownLng = null;
        }
        api.searchMap(MapFragment.this.query, lastKnownLat, lastKnownLng);
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if(newText != null){
            if(newText.length() > 1){
                queryMaps(newText);
            }
        }
        return false;
    }

    @Override
    public void onTaskComplete(Object result, int customTag) {
        callInProgress = false;
        switch(customTag){
            case MapzenAPICalls.TAG_MAPZEN_CONNECTIVITY_ISSUE:
                //todo Make call here to update snackbar with no internet;
                break;

            case MapzenAPICalls.TAG_MAPZEN_SUCCESS:
                MapzenPOJO pojo = (MapzenPOJO) result;
                List<MapzenPOJO.MapzenFeatures> checkingFeatures = pojo.getFeatures();
                if(MiscUtilities.isListNullOrEmpty(checkingFeatures)){
                    if(this.secondaryCall){
                        this.secondaryCall = false;
                    } else {
                        String str = query;
                        if(!StringUtilities.isNullOrEmpty(str)){
                            if(Character.isDigit(str.charAt(0))){
                                int pos = 0;
                                for(int i = 0; i < str.length(); i++){
                                    try {
                                        if (Character.isDigit(str.charAt(i))) {
                                            pos = i;
                                        } else {
                                            break;
                                        }
                                    } catch (Exception e){
                                        pos = i - 1;
                                        if(pos < 0){
                                            pos = 0;
                                        }
                                        break;
                                    }
                                }
                                String sub1 = str.substring(pos);
                                if(!StringUtilities.isNullOrEmpty(sub1)){
                                    this.query = sub1;
                                    this.secondaryCall = true;
                                    makeSearchCall();
                                    return;
                                }
                            }
                        }
                    }
                }
                this.setSearchResults(pojo);
                break;

            case MapzenAPICalls.TAG_MAPZEN_FAILURE:
                this.setSearchResults(null);
                break;

            case MapzenAPICalls.TAG_MAPZEN_INVALID_QUERY:
                this.setSearchResults(null);
                break;

            case MapzenAPICalls.TAG_MAPZEN_TBD_1:
                this.setSearchResults(null);
                break;

            case MapzenAPICalls.TAG_MAPZEN_TBD_2:
                this.setSearchResults(null);
                break;
        }
    }

    /**
     * Manage clicks on the list
     * @param parent
     * @param view
     * @param position
     * @param id
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

        if(this.searchResultsList == null){
            this.searchResultsList = new ArrayList<>();
        }
        if(this.searchResultsList.size() == 0){
            return;
        } else {
            MapzenSimpleObject pojo = searchResultsList.get(position);
            double latitude = pojo.getLatitude();
            double longitude = pojo.getLongitude();
            if(latitude !=  0 && longitude != 0){
                moveCamera(latitude, longitude);
            }
            showListview(false);
        }
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if(v == this.searchView){
            if(hasFocus){
                if(fragment_map_search_listview.getVisibility() != View.VISIBLE){
                    showListview(true);
                }
            } else {
                if(fragment_map_search_listview.getVisibility() == View.VISIBLE){
                    showListview(false);
                }
            }
        }
    }

    @Override
    public void onMapLongClick(LatLng point) {
        if(this.lastDrawnCircle != null){
            this.lastDrawnCircle.remove();
        }
        Projection projection = googleMap.getProjection();
        View view = getChildFragmentManager().findFragmentById(R.id.fragment_map_map).getView();
        double screenHeight = view.getHeight();
        L.m("screenHeight = " + screenHeight);
        float currentZoom = googleMap.getCameraPosition().zoom;
        L.m("currentZoom = " + currentZoom);
        double dpPerdegree = 256.0 * Math.pow(2, currentZoom) / 170.0;
        L.m("dpPerdegree = " + dpPerdegree);
        double currentLat = point.latitude;
        L.m("currentLat = " + currentLat);
        double currentLng = point.longitude;
        L.m("currentLng = " + currentLng);
        double screenHeight10 = 10 * (screenHeight / 100);
        L.m("screenHeight30 = " + screenHeight10);
        double degree10 = screenHeight10 / dpPerdegree;
        L.m("degree30 = " + degree10);

        googleMap.getProjection();
        //Point center = ();
        LatLng radiusLatLng = googleMap.getProjection().fromScreenLocation(
                new Point((int) (currentLat + degree10), (int)(currentLng + degree10)));

        // Create the circle.
        CircleOptions options = new CircleOptions();
        options.center(point);
        options.radius(50); //toRadiusMeters(point, radiusLatLng) // TODO: 2017-02-21 insert code here to match pull from scalebar
        options.strokeColor(R.color.white);
        options.fillColor(R.color.SemiTransparentBlue);
        options.clickable(true);
        this.lastDrawnCircle = googleMap.addCircle(options);
        this.googleMap.setOnCircleClickListener(this);
    }

    /** Generate LatLng of radius marker */
    private static LatLng toRadiusLatLng(LatLng center, double radiusMeters) {
        double radiusAngle = Math.toDegrees(radiusMeters / RADIUS_OF_EARTH_METERS) /
                Math.cos(Math.toRadians(center.latitude));
        return new LatLng(center.latitude, center.longitude + radiusAngle);
    }

    /** Convert Lat long center + radius to meters */
    private static double toRadiusMeters(LatLng center, LatLng radius) {
        float[] result = new float[1];
        Location.distanceBetween(center.latitude, center.longitude,
                radius.latitude, radius.longitude, result);
        return result[0];
    }

    @Override
    public void onCircleClick(Circle circle) {
        //Do something here with circle click
        L.m("circle clicked");
    }

    private void enableScaleBar(){
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(800, 800);

        params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        //params.addRule(RelativeLayout.CENTER_VERTICAL);

        mScaleBar = new ScaleBar(getActivity(), this.googleMap);
        mScaleBar.setLayoutParams(params);
        fragment_map_main_layout.addView(mScaleBar);
    }

    @Override
    public void onCameraMove() {
        //Used to invalidate the scale bar when they move
        if(mScaleBar != null){
            mScaleBar.invalidate();
            //mScaleBar.invalidateNumbersOnly();
            AnimationUtilities.animateMyView(mScaleBar, (100), Techniques.FadeIn);
            dismissScalebarAfterXSeconds(NUM_SECONDS_ON_SCALEBAR_HIDE);
        }
    }

    /**
     * Hide / dismiss the scale bar after X seconds so that it will fade away and not
     * stay permanently on the screen.
     * @param seconds Num seconds to stay on the screen before it disappears
     */
    private void dismissScalebarAfterXSeconds(int seconds){
        seconds *= 1000; //Milliseconds
        if(scaleBarTimer == null){
            scaleBarTimer = new Timer();
        }
        scaleBarTimer.cancel();
        scaleBarTimer = new Timer();
        scaleBarTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            AnimationUtilities.animateMyView(mScaleBar,
                                    (int)(Constants.ONE_SECOND * 1.4), Techniques.FadeOut);
                        }
                    });
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        }, seconds);
    }

    @Override
    public boolean handleMessage(Message msg) {
        L.m("684");
        if(msg != null){
            Bundle bundle = msg.getData();
            if(bundle != null){

                L.m("689");
                boolean bool = bundle.getBoolean(DISMISS_SCALE_BAR, false);
                if(bool){
                    L.m("692");

                }
            }

        }
        return false;
    }
}