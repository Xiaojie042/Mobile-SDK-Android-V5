package dji.v5.ux.sample.showcase.defaultlayout;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.dji.wpmzsdk.common.data.Template;
import com.dji.wpmzsdk.manager.WPMZManager;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import dji.sdk.wpmz.value.mission.Wayline;
import dji.sdk.wpmz.value.mission.WaylineAltitudeMode;
import dji.sdk.wpmz.value.mission.WaylineCoordinateMode;
import dji.sdk.wpmz.value.mission.WaylineCoordinateParam;
import dji.sdk.wpmz.value.mission.WaylineDroneInfo;
import dji.sdk.wpmz.value.mission.WaylineExecuteWaypoint;
import dji.sdk.wpmz.value.mission.WaylineExitOnRCLostAction;
import dji.sdk.wpmz.value.mission.WaylineExitOnRCLostBehavior;
import dji.sdk.wpmz.value.mission.WaylineFinishedAction;
import dji.sdk.wpmz.value.mission.WaylineFlyToWaylineMode;
import dji.sdk.wpmz.value.mission.WaylineLocationCoordinate2D;
import dji.sdk.wpmz.value.mission.WaylineLocationCoordinate3D;
import dji.sdk.wpmz.value.mission.WaylineMission;
import dji.sdk.wpmz.value.mission.WaylineMissionConfig;
import dji.sdk.wpmz.value.mission.WaylinePayloadInfo;
import dji.sdk.wpmz.value.mission.WaylinePositioningType;
import dji.sdk.wpmz.value.mission.WaylineTemplateWaypointInfo;
import dji.sdk.wpmz.value.mission.WaylineWaypoint;
import dji.sdk.wpmz.value.mission.WaylineWaypointPitchMode;
import dji.sdk.wpmz.value.mission.WaylineWaypointTurnMode;
import dji.sdk.wpmz.value.mission.WaylineWaypointYawMode;
import dji.sdk.wpmz.value.mission.WaylineWaypointYawParam;
import dji.v5.common.callback.CommonCallbacks;
import dji.v5.common.error.IDJIError;
import dji.v5.manager.aircraft.waypoint3.WaylineExecutingInfoListener;
import dji.v5.manager.aircraft.waypoint3.WaypointActionListener;
import dji.v5.manager.aircraft.waypoint3.WaypointMissionExecuteStateListener;
import dji.v5.manager.aircraft.waypoint3.WaypointMissionManager;
import dji.v5.manager.aircraft.waypoint3.model.WaylineExecutingInfo;
import dji.v5.manager.aircraft.waypoint3.model.WaypointMissionExecuteState;
import dji.v5.ux.R;
import dji.v5.ux.map.MapWidget;
import dji.v5.ux.mapkit.core.camera.DJICameraUpdateFactory;
import dji.v5.ux.mapkit.core.maps.DJIMap;
import dji.v5.ux.mapkit.core.models.DJIBitmapDescriptorFactory;
import dji.v5.ux.mapkit.core.models.DJICameraPosition;
import dji.v5.ux.mapkit.core.models.DJILatLng;
import dji.v5.ux.mapkit.core.models.DJILatLngBounds;
import dji.v5.ux.mapkit.core.models.annotations.DJIMarker;
import dji.v5.ux.mapkit.core.models.annotations.DJIMarkerOptions;
import dji.v5.ux.mapkit.core.models.annotations.DJIPolyline;
import dji.v5.ux.mapkit.core.models.annotations.DJIPolylineOptions;

class DefaultLayoutWaylineManager {

    static final int REQUEST_IMPORT_KMZ = 5107;

    private static final String TAG = "DefaultWayline";
    private static final double DEFAULT_WAYPOINT_HEIGHT = 100d;
    private static final double DEFAULT_WAYPOINT_SPEED = 5d;
    private static final int ROUTE_COLOR = 0xFF007AFF;
    private static final int CARD_COLOR = 0xCC2C2C2E;
    private static final int CARD_STROKE_COLOR = 0xFF3A3A3C;
    private static final int SELECTED_CARD_COLOR = 0x33007AFF;
    private static final int TEXT_PRIMARY = Color.WHITE;
    private static final int TEXT_SECONDARY = 0xFFAEAEB2;
    private static final String DEFAULT_ROUTE_NAME_PREFIX = "Waypoint";
    private static final String WAYPOINT_DOCUMENT_ROOT = "DJI/Waypoints";
    private static final String ROUTE_METADATA_ENTRY = "dji_wayline_meta.json";
    private static final int[] ROUTE_COLOR_OPTIONS = {
            0xFF007AFF,
            0xFFFF3B30,
            0xFF34C759,
            0xFFFFCC00,
            0xFFFF9500,
            0xFFFFFFFF
    };

    private final AppCompatActivity activity;
    private final MapWidget mapWidget;
    private final View panel;
    private final View panelScrim;
    private final View entryGroup;
    private final TextView routeEntryButton;
    private final TextView waypointEntryButton;
    private final TextView closePanelButton;
    private final TextView routeTab;
    private final TextView waypointTab;
    private final ScrollView routeContent;
    private final ScrollView waypointContent;
    private final TextView statusText;
    private final TextView selectedIdsText;
    private final TextView executeStateText;
    private final TextView executeInfoText;
    private final TextView totalLengthText;
    private final TextView waypointCountText;
    private final TextView estimateTimeText;
    private final TextView waypointHintText;
    private final TextView waypointDetailTitle;
    private final EditText routeNameEdit;
    private final EditText waypointLatitudeEdit;
    private final EditText waypointLongitudeEdit;
    private final EditText waypointHeightEdit;
    private final EditText waypointSpeedEdit;
    private final EditText waypointActionEdit;
    private final Button addPointButton;
    private final Button deletePointButton;
    private final Button startButton;
    private final Button clearRouteButton;
    private final Button saveEditButton;
    private final Button discardEditButton;
    private final Button centerWaypointButton;
    private final LinearLayout routeListView;
    private final LinearLayout waypointListView;
    private final LinearLayout colorPickerView;
    private final Switch routeVisibilitySwitch;
    private final View waypointDetailCard;
    private final List<RouteUiModel> routeModels = new ArrayList<>();
    private final List<EditableWaypoint> editWaypoints = new ArrayList<>();
    private final List<DJIMarker> pointMarkers = new ArrayList<>();
    private final List<DJIMarker> distanceMarkers = new ArrayList<>();
    private final List<DJIMarker> insertPointMarkers = new ArrayList<>();
    private final List<DJIPolyline> routeLines = new ArrayList<>();
    private final List<Integer> selectedWaylineIds = new ArrayList<>();

    private RouteUiModel currentRoute;
    private RouteUiModel editingRoute;
    private RouteUiModel editingSourceRoute;
    private String currentMissionPath = "";
    private boolean addPointMode = false;
    private boolean routeVisible = true;
    private boolean mapExpanded = false;
    private boolean panelOpen = false;
    private boolean panelShowingRoute = true;
    private boolean updatingRouteName = false;
    private boolean updatingWaypointFields = false;
    private int selectedWaypointIndex = -1;

    private final DJIMap.OnMapClickListener addPointListener = this::addWaypointFromMap;
    private final DJIMap.OnMapLongClickListener addPointLongClickListener = this::addWaypointFromMap;
    private final DJIMap.OnMarkerClickListener markerClickListener = this::onMarkerClick;
    private final DJIMap.OnMarkerDragListener markerDragListener = new DJIMap.OnMarkerDragListener() {
        @Override
        public void onMarkerDragStart(DJIMarker marker) {
            int index = getWaypointMarkerIndex(marker);
            if (isValidWaypointIndex(index)) {
                selectedWaypointIndex = index;
                refreshWaypointList(true);
            }
        }

        @Override
        public void onMarkerDrag(DJIMarker marker) {
            // Route geometry is refreshed on drag end to keep the map responsive.
        }

        @Override
        public void onMarkerDragEnd(DJIMarker marker) {
            if (editingRoute == null) {
                return;
            }
            int index = getWaypointMarkerIndex(marker);
            if (!isValidWaypointIndex(index) || marker.getPosition() == null) {
                return;
            }
            EditableWaypoint waypoint = editWaypoints.get(index);
            waypoint.latitude = marker.getPosition().getLatitude();
            waypoint.longitude = marker.getPosition().getLongitude();
            selectedWaypointIndex = index;
            markEditingRouteDirty(false);
            refreshMapOverlays();
            refreshWaypointList(true);
            statusText.setText("航点 " + (index + 1) + " 坐标已更新");
        }
    };
    private final WaypointMissionExecuteStateListener missionStateListener;
    private final WaylineExecutingInfoListener waylineExecutingInfoListener;
    private final WaypointActionListener waypointActionListener;

    DefaultLayoutWaylineManager(AppCompatActivity activity, MapWidget mapWidget, View rootView) {
        this.activity = activity;
        this.mapWidget = mapWidget;
        panel = rootView.findViewById(R.id.uxsdk_wayline_panel);
        panelScrim = rootView.findViewById(R.id.uxsdk_wayline_panel_scrim);
        entryGroup = rootView.findViewById(R.id.uxsdk_wayline_entry_group);
        routeEntryButton = rootView.findViewById(R.id.uxsdk_wayline_entry_route);
        waypointEntryButton = rootView.findViewById(R.id.uxsdk_wayline_entry_waypoint);
        closePanelButton = rootView.findViewById(R.id.uxsdk_wayline_panel_close);
        routeTab = rootView.findViewById(R.id.uxsdk_wayline_tab_route);
        waypointTab = rootView.findViewById(R.id.uxsdk_wayline_tab_waypoint);
        routeContent = rootView.findViewById(R.id.uxsdk_wayline_route_content);
        waypointContent = rootView.findViewById(R.id.uxsdk_wayline_waypoint_content);
        statusText = rootView.findViewById(R.id.uxsdk_wayline_status);
        selectedIdsText = rootView.findViewById(R.id.uxsdk_wayline_selected_ids);
        executeStateText = rootView.findViewById(R.id.uxsdk_wayline_execute_state);
        executeInfoText = rootView.findViewById(R.id.uxsdk_wayline_execute_info);
        totalLengthText = rootView.findViewById(R.id.uxsdk_wayline_total_length);
        waypointCountText = rootView.findViewById(R.id.uxsdk_wayline_waypoint_count);
        estimateTimeText = rootView.findViewById(R.id.uxsdk_wayline_estimate_time);
        waypointHintText = rootView.findViewById(R.id.uxsdk_waypoint_hint);
        waypointDetailTitle = rootView.findViewById(R.id.uxsdk_waypoint_detail_title);
        routeNameEdit = rootView.findViewById(R.id.uxsdk_wayline_name_edit);
        waypointLatitudeEdit = rootView.findViewById(R.id.uxsdk_waypoint_latitude);
        waypointLongitudeEdit = rootView.findViewById(R.id.uxsdk_waypoint_longitude);
        waypointHeightEdit = rootView.findViewById(R.id.uxsdk_waypoint_height);
        waypointSpeedEdit = rootView.findViewById(R.id.uxsdk_waypoint_speed);
        waypointActionEdit = rootView.findViewById(R.id.uxsdk_waypoint_action);
        addPointButton = rootView.findViewById(R.id.uxsdk_wayline_add_point);
        deletePointButton = rootView.findViewById(R.id.uxsdk_wayline_delete_point);
        startButton = rootView.findViewById(R.id.uxsdk_wayline_start);
        clearRouteButton = rootView.findViewById(R.id.uxsdk_wayline_clear);
        saveEditButton = rootView.findViewById(R.id.uxsdk_wayline_save_kmz);
        discardEditButton = rootView.findViewById(R.id.uxsdk_wayline_discard_edit);
        centerWaypointButton = rootView.findViewById(R.id.uxsdk_waypoint_center);
        routeListView = rootView.findViewById(R.id.uxsdk_wayline_route_list);
        waypointListView = rootView.findViewById(R.id.uxsdk_waypoint_list);
        colorPickerView = rootView.findViewById(R.id.uxsdk_wayline_color_picker);
        routeVisibilitySwitch = rootView.findViewById(R.id.uxsdk_wayline_visibility_switch);
        waypointDetailCard = rootView.findViewById(R.id.uxsdk_waypoint_detail_card);

        missionStateListener = state -> runOnUiThread(() -> {
            executeStateText.setText("任务状态：" + state.name());
            if (state == WaypointMissionExecuteState.FINISHED) {
                showToast("任务已完成");
            }
        });
        waylineExecutingInfoListener = new WaylineExecutingInfoListener() {
            @Override
            public void onWaylineExecutingInfoUpdate(WaylineExecutingInfo info) {
                runOnUiThread(() -> executeInfoText.setText(
                        "执行信息：Wayline " + info.getWaylineID()
                                + " / 航点 " + info.getCurrentWaypointIndex()
                                + "\n任务：" + info.getMissionFileName()));
            }

            @Override
            public void onWaylineExecutingInterruptReasonUpdate(@Nullable IDJIError error) {
                if (error != null) {
                    runOnUiThread(() -> executeInfoText.setText("中断原因：" + getErrorMessage(error)));
                }
            }
        };
        waypointActionListener = new WaypointActionListener() {
            @Override
            public void onExecutionStart(int actionId) {
                runOnUiThread(() -> executeInfoText.setText("航点动作开始：" + actionId));
            }

            @Override
            public void onExecutionStart(int actionGroup, int actionId) {
                runOnUiThread(() -> executeInfoText.setText("航点动作开始：" + actionGroup + " / " + actionId));
            }

            @Override
            public void onExecutionFinish(int actionId, @Nullable IDJIError error) {
                runOnUiThread(() -> executeInfoText.setText(
                        "航点动作结束：" + actionId
                                + (error == null ? "" : "\n错误：" + getErrorMessage(error))));
            }

            @Override
            public void onExecutionFinish(int actionGroup, int actionId, @Nullable IDJIError error) {
                runOnUiThread(() -> executeInfoText.setText(
                        "航点动作结束：" + actionGroup + " / " + actionId
                                + (error == null ? "" : "\n错误：" + getErrorMessage(error))));
            }
        };

        rootView.findViewById(R.id.uxsdk_wayline_import_kmz).setOnClickListener(v -> openKmzChooser());
        saveEditButton.setOnClickListener(v -> saveCurrentEditMission(true));
        clearRouteButton.setOnClickListener(v -> clearMission());
        rootView.findViewById(R.id.uxsdk_wayline_upload).setOnClickListener(v -> uploadCurrentMission());
        startButton.setOnClickListener(v -> startMission());
        rootView.findViewById(R.id.uxsdk_wayline_pause).setOnClickListener(v -> pauseMission());
        rootView.findViewById(R.id.uxsdk_wayline_resume).setOnClickListener(v -> resumeMission());
        rootView.findViewById(R.id.uxsdk_wayline_stop).setOnClickListener(v -> stopMission());
        addPointButton.setOnClickListener(v -> setAddPointMode(!addPointMode));
        deletePointButton.setOnClickListener(v -> deleteSelectedWaypoint());
        discardEditButton.setOnClickListener(v -> discardEdit());
        centerWaypointButton.setOnClickListener(v -> centerSelectedWaypoint());
        closePanelButton.setOnClickListener(v -> closePanel());
        routeEntryButton.setOnClickListener(v -> togglePanel(true));
        waypointEntryButton.setOnClickListener(v -> togglePanel(false));
        routeTab.setOnClickListener(v -> showRoutePanel());
        waypointTab.setOnClickListener(v -> showWaypointPanel());
        routeVisibilitySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            routeVisible = isChecked;
            refreshMapOverlays();
            statusText.setText(isChecked ? "航线已显示" : "航线已隐藏");
        });
        routeNameEdit.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                RouteUiModel targetRoute = getActiveRoute();
                if (updatingRouteName || targetRoute == null) {
                    return;
                }
                String name = editable.toString().trim();
                targetRoute.name = TextUtils.isEmpty(name) ? "未命名航线" : name;
                refreshRouteList();
            }
        });
        TextWatcher waypointDetailWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                applyWaypointDetailEdits();
            }
        };
        waypointLatitudeEdit.addTextChangedListener(waypointDetailWatcher);
        waypointLongitudeEdit.addTextChangedListener(waypointDetailWatcher);
        waypointHeightEdit.addTextChangedListener(waypointDetailWatcher);
        waypointSpeedEdit.addTextChangedListener(waypointDetailWatcher);
        waypointActionEdit.addTextChangedListener(waypointDetailWatcher);

        WPMZManager.getInstance().init(activity.getApplicationContext());
        WaypointMissionManager.getInstance().addWaypointMissionExecuteStateListener(missionStateListener);
        WaypointMissionManager.getInstance().addWaylineExecutingInfoListener(waylineExecutingInfoListener);
        WaypointMissionManager.getInstance().addWaypointActionListener(waypointActionListener);
        showRoutePanelImmediate();
        updateSelectedWaylineStatus();
        refreshRouteList();
        refreshRouteInfo();
        refreshWaypointList(true);
    }

    void onMapReady() {
        DJIMap map = mapWidget.getMap();
        if (map != null) {
            map.removeOnMarkerClickListener(markerClickListener);
            map.setOnMarkerClickListener(markerClickListener);
            map.removeOnMarkerDragListener(markerDragListener);
            map.setOnMarkerDragListener(markerDragListener);
        }
        if (addPointMode) {
            applyMapClickListener();
        }
        refreshMapOverlays();
    }

    void setExpanded(boolean expanded) {
        mapExpanded = expanded;
        entryGroup.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (expanded) {
            refreshRouteList();
            refreshRouteInfo();
            refreshWaypointList(true);
            closePanelImmediate();
        } else {
            closePanelImmediate();
            setAddPointMode(false);
        }
    }

    void bringPanelToFront() {
        entryGroup.bringToFront();
        panelScrim.bringToFront();
        panel.bringToFront();
    }

    boolean handleBackPressed() {
        return panelOpen;
    }

    boolean onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode != REQUEST_IMPORT_KMZ) {
            return false;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return true;
        }
        importKmz(data.getData());
        return true;
    }

    void onDestroy() {
        setAddPointMode(false);
        DJIMap map = mapWidget.getMap();
        if (map != null) {
            map.removeOnMarkerClickListener(markerClickListener);
            map.removeOnMarkerDragListener(markerDragListener);
            map.removeOnMapLongClickListener(addPointLongClickListener);
        }
        WaypointMissionManager.getInstance().removeWaypointMissionExecuteStateListener(missionStateListener);
        WaypointMissionManager.getInstance().removeWaylineExecutingInfoListener(waylineExecutingInfoListener);
        WaypointMissionManager.getInstance().removeWaypointActionListener(waypointActionListener);
        clearMapOverlays();
    }

    private void showRoutePanel() {
        setTabState(true);
        animateContentSwitch(routeContent, waypointContent);
    }

    private void showWaypointPanel() {
        setTabState(false);
        animateContentSwitch(waypointContent, routeContent);
    }

    private void showRoutePanelImmediate() {
        setTabState(true);
        routeContent.setAlpha(1f);
        waypointContent.setAlpha(0f);
        routeContent.setVisibility(View.VISIBLE);
        waypointContent.setVisibility(View.GONE);
    }

    private void togglePanel(boolean routePanel) {
        if (panelOpen && panelShowingRoute == routePanel) {
            closePanel();
            return;
        }
        openPanel(routePanel);
    }

    private void openPanel(boolean routePanel) {
        if (!mapExpanded) {
            return;
        }
        panelShowingRoute = routePanel;
        if (routePanel) {
            showRoutePanelImmediate();
        } else {
            setTabState(false);
            waypointContent.setAlpha(1f);
            waypointContent.setVisibility(View.VISIBLE);
            routeContent.setAlpha(0f);
            routeContent.setVisibility(View.GONE);
        }
        panelOpen = true;
        panelScrim.setVisibility(View.GONE);
        panel.setVisibility(View.VISIBLE);
        panel.setTranslationX(-Math.max(panel.getWidth(), dp(320)));
        panel.animate()
                .translationX(0f)
                .setDuration(250L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        bringPanelToFront();
    }

    private void closePanel() {
        if (!panelOpen) {
            return;
        }
        panelOpen = false;
        panelScrim.setVisibility(View.GONE);
        panel.animate()
                .translationX(-Math.max(panel.getWidth(), dp(320)))
                .setDuration(220L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (!panelOpen) {
                        panel.setVisibility(View.GONE);
                        panel.setTranslationX(0f);
                    }
                })
                .start();
    }

    private void closePanelImmediate() {
        panelOpen = false;
        panelScrim.setVisibility(View.GONE);
        panel.animate().cancel();
        panel.setTranslationX(0f);
        panel.setVisibility(View.GONE);
    }

    private void setTabState(boolean routeSelected) {
        routeTab.setBackgroundResource(routeSelected
                ? R.drawable.uxsdk_wayline_tab_selected_bg
                : R.drawable.uxsdk_wayline_tab_unselected_bg);
        waypointTab.setBackgroundResource(routeSelected
                ? R.drawable.uxsdk_wayline_tab_unselected_bg
                : R.drawable.uxsdk_wayline_tab_selected_bg);
        routeTab.setTextColor(routeSelected ? TEXT_PRIMARY : 0x99FFFFFF);
        waypointTab.setTextColor(routeSelected ? 0x99FFFFFF : TEXT_PRIMARY);
    }

    private void animateContentSwitch(View showView, View hideView) {
        if (showView.getVisibility() == View.VISIBLE) {
            return;
        }
        hideView.animate().alpha(0f).setDuration(120L).withEndAction(() -> hideView.setVisibility(View.GONE)).start();
        showView.setAlpha(0f);
        showView.setVisibility(View.VISIBLE);
        showView.animate().alpha(1f).setDuration(160L).start();
    }

    private void openKmzChooser() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/vnd.google-earth.kmz",
                "application/octet-stream",
                "application/zip"
        });
        activity.startActivityForResult(intent, REQUEST_IMPORT_KMZ);
    }

    private void importKmz(Uri uri) {
        String displayName = getDisplayName(uri);
        if (TextUtils.isEmpty(displayName)) {
            displayName = "imported_wayline.kmz";
        }
        if (!displayName.toLowerCase(Locale.US).endsWith(".kmz")) {
            showToast("请选择 KMZ 文件");
            return;
        }

        File outFile = getAvailableKmzFile(displayName);
        try (InputStream inputStream = activity.getContentResolver().openInputStream(uri);
             FileOutputStream outputStream = new FileOutputStream(outFile)) {
            if (inputStream == null) {
                showToast("无法读取 KMZ 文件");
                return;
            }
            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
        } catch (IOException e) {
            showToast("导入 KMZ 失败：" + e.getMessage());
            return;
        }

        RouteUiModel route;
        try {
            route = parseKmzRoute(outFile, stripKmzExtension(displayName));
        } catch (Exception e) {
            showToast("解析 KMZ 失败：" + e.getMessage());
            return;
        }
        if (route.waypoints.isEmpty()) {
            showToast("KMZ 中没有航线航点");
            return;
        }

        addOrReplaceRoute(route);
        selectRoute(route, true);
        showRoutePanel();
        statusText.setText("已导入：" + outFile.getName());
    }

    private RouteUiModel parseKmzRoute(File file, String routeName) {
        RouteUiModel route = new RouteUiModel(routeName, file.getAbsolutePath());
        List<Wayline> waylines = WPMZManager.getInstance()
                .getKMZInfo(file.getAbsolutePath())
                .getWaylineWaylinesParseInfo()
                .getWaylines();
        if (waylines == null) {
            return route;
        }
        for (Wayline wayline : waylines) {
            List<EditableWaypoint> segment = new ArrayList<>();
            List<WaylineExecuteWaypoint> waypoints = wayline.getWaypoints();
            if (waypoints == null) {
                continue;
            }
            for (WaylineExecuteWaypoint waypoint : waypoints) {
                if (waypoint.getLocation() == null) {
                    continue;
                }
                EditableWaypoint editableWaypoint = new EditableWaypoint(
                        waypoint.getLocation().getLatitude(),
                        waypoint.getLocation().getLongitude(),
                        DEFAULT_WAYPOINT_HEIGHT,
                        DEFAULT_WAYPOINT_SPEED,
                        "");
                segment.add(editableWaypoint);
                route.waypoints.add(editableWaypoint);
            }
            if (!segment.isEmpty()) {
                route.segments.add(segment);
            }
        }
        applyRouteMetadata(file, route);
        route.recalculate();
        return route;
    }

    private void addOrReplaceRoute(RouteUiModel route) {
        for (int i = 0; i < routeModels.size(); i++) {
            RouteUiModel existingRoute = routeModels.get(i);
            if (!TextUtils.isEmpty(route.path) && TextUtils.equals(existingRoute.path, route.path)) {
                routeModels.set(i, route);
                return;
            }
        }
        routeModels.add(0, route);
    }

    private void selectRoute(RouteUiModel route, boolean centerOnMap) {
        if (editingRoute != null) {
            showToast("请先保存或放弃当前编辑");
            return;
        }
        currentRoute = route;
        editWaypoints.clear();
        editWaypoints.addAll(route.waypoints);
        selectedWaypointIndex = -1;
        currentMissionPath = TextUtils.isEmpty(route.path) ? "" : route.path;
        selectedWaylineIds.clear();
        setAddPointMode(false);
        refreshMapOverlays();
        refreshRouteList();
        refreshRouteInfo();
        refreshWaypointList(true);
        updateSelectedWaylineStatus();
        if (centerOnMap) {
            centerRoute(route);
        }
    }

    private void addWaypointFromMap(DJILatLng latLng) {
        if (editingRoute == null) {
            startNewRouteEdit();
        }
        if (latLng == null || !latLng.isAvailable()) {
            showToast("地图坐标无效");
            return;
        }
        EditableWaypoint waypoint = new EditableWaypoint(
                latLng.getLatitude(),
                latLng.getLongitude(),
                DEFAULT_WAYPOINT_HEIGHT,
                DEFAULT_WAYPOINT_SPEED,
                "");
        int insertIndex = editWaypoints.size();
        editWaypoints.add(insertIndex, waypoint);
        selectedWaypointIndex = insertIndex;
        markEditingRouteDirty(true);
        refreshMapOverlays();
        refreshRouteList();
        refreshRouteInfo();
        refreshWaypointList(true);
        statusText.setText("已添加航点：" + editWaypoints.size()
                + "，高度 " + (int) DEFAULT_WAYPOINT_HEIGHT
                + "m，速度 " + formatNumber(DEFAULT_WAYPOINT_SPEED) + "m/s");
    }

    private void startNewRouteEdit() {
        editingSourceRoute = null;
        editingRoute = new RouteUiModel(defaultRouteName(), "");
        currentRoute = editingRoute;
        editWaypoints.clear();
        selectedWaypointIndex = -1;
        selectedWaylineIds.clear();
        currentMissionPath = "";
        updateSelectedWaylineStatus();
        updateEditButtonState();
        statusText.setText("新航迹编辑中，点击地图添加航点");
    }

    private void deleteSelectedWaypoint() {
        if (editingRoute == null) {
            showToast("请先点击航线列表右侧编辑，或新建航迹");
            return;
        }
        if (!isValidWaypointIndex(selectedWaypointIndex)) {
            showToast("请先选择一个航点");
            return;
        }
        int removedIndex = selectedWaypointIndex;
        editWaypoints.remove(removedIndex);
        if (editWaypoints.isEmpty()) {
            selectedWaypointIndex = -1;
        } else {
            selectedWaypointIndex = Math.min(removedIndex, editWaypoints.size() - 1);
        }
        markEditingRouteDirty(true);
        refreshMapOverlays();
        refreshRouteList();
        refreshRouteInfo();
        refreshWaypointList(true);
        statusText.setText("已删除航点 " + (removedIndex + 1));
    }

    private void saveCurrentEditMission(boolean showSuccessToast) {
        if (editingRoute == null) {
            showToast("当前没有正在编辑的航迹");
            return;
        }
        if (editWaypoints.size() < 2) {
            showToast("至少添加 2 个航点后才能保存航线");
            return;
        }

        String name = normalizeRouteFileBaseName(editingRoute.name);
        if (TextUtils.isEmpty(name)) {
            name = defaultRouteName();
        }
        editingRoute.name = name;
        File outFile = getSaveFileForEditingRoute(name);
        try {
            WaylineMission waylineMission = createWaylineMission();
            WaylineMissionConfig missionConfig = createMissionConfig();
            Template template = createTemplate(editWaypoints);
            WPMZManager.getInstance().generateKMZFile(outFile.getAbsolutePath(), waylineMission, missionConfig, template);
            writeRouteMetadata(outFile, editingRoute);
            Log.i(TAG, "Waypoint KMZ saved: " + outFile.getAbsolutePath());
        } catch (Exception e) {
            showToast("保存航线失败：" + e.getMessage());
            return;
        }

        editingRoute.path = outFile.getAbsolutePath();
        editingRoute.waypoints.clear();
        editingRoute.waypoints.addAll(copyWaypoints(editWaypoints));
        editingRoute.segments.clear();
        editingRoute.segments.add(copyWaypoints(editingRoute.waypoints));
        editingRoute.recalculate();

        if (editingSourceRoute == null) {
            routeModels.add(0, editingRoute);
            currentRoute = editingRoute;
        } else {
            copyRoute(editingRoute, editingSourceRoute);
            currentRoute = editingSourceRoute;
        }

        editingRoute = null;
        editingSourceRoute = null;
        editWaypoints.clear();
        selectedWaypointIndex = -1;
        currentMissionPath = currentRoute.path;
        selectedWaylineIds.clear();
        updateSelectedWaylineStatus();
        setAddPointMode(false);
        refreshRouteList();
        refreshRouteInfo();
        refreshWaypointList(true);
        refreshMapOverlays();
        statusText.setText("已保存：" + outFile.getName());
        if (showSuccessToast) {
            showToast("航线 KMZ 已保存");
        }
    }

    private void uploadCurrentMission() {
        if (editingRoute != null && editWaypoints.size() >= 2) {
            saveCurrentEditMission(false);
        }
        if (!hasMissionFile()) {
            showToast("请先导入 KMZ 或添加航点保存航线");
            return;
        }

        statusText.setText("正在上传：" + new File(currentMissionPath).getName());
        WaypointMissionManager.getInstance().pushKMZFileToAircraft(currentMissionPath,
                new CommonCallbacks.CompletionCallbackWithProgress<Double>() {
                    @Override
                    public void onProgressUpdate(Double progress) {
                        runOnUiThread(() -> statusText.setText(String.format(Locale.US, "上传进度：%.1f%%", progress)));
                    }

                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            statusText.setText("KMZ 上传成功");
                            showToast("KMZ 上传成功");
                        });
                    }

                    @Override
                    public void onFailure(IDJIError error) {
                        runOnUiThread(() -> {
                            statusText.setText("KMZ 上传失败：" + getErrorMessage(error));
                            showToast("KMZ 上传失败：" + getErrorMessage(error));
                        });
                    }
                });
    }

    private void showWaylineIdDialog() {
        if (!hasMissionFile()) {
            showToast("请先导入或保存 KMZ");
            return;
        }
        List<Integer> waylineIds = WaypointMissionManager.getInstance().getAvailableWaylineIDs(currentMissionPath);
        if (waylineIds == null || waylineIds.isEmpty()) {
            showToast("当前 KMZ 未读取到可选航线 ID");
            return;
        }

        String[] items = new String[waylineIds.size()];
        boolean[] checked = new boolean[waylineIds.size()];
        for (int i = 0; i < waylineIds.size(); i++) {
            Integer id = waylineIds.get(i);
            items[i] = String.valueOf(id);
            checked[i] = selectedWaylineIds.contains(id);
        }

        new AlertDialog.Builder(activity)
                .setTitle("选择航线 ID")
                .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> {
                    Integer id = waylineIds.get(which);
                    if (isChecked && !selectedWaylineIds.contains(id)) {
                        selectedWaylineIds.add(id);
                    } else if (!isChecked) {
                        selectedWaylineIds.remove(id);
                    }
                    updateSelectedWaylineStatus();
                })
                .setPositiveButton("确定", (dialog, which) -> updateSelectedWaylineStatus())
                .setNegativeButton("全部航线", (dialog, which) -> {
                    selectedWaylineIds.clear();
                    updateSelectedWaylineStatus();
                })
                .show();
    }

    private void startMission() {
        if (editingRoute != null && editWaypoints.size() >= 2) {
            saveCurrentEditMission(false);
        }
        if (!hasMissionFile()) {
            showToast("请先导入或保存 KMZ 文件");
            return;
        }
        WaypointMissionManager.getInstance().startMission(getMissionId(), selectedWaylineIds, createCompletionCallback("开始任务"));
    }

    private void pauseMission() {
        WaypointMissionManager.getInstance().pauseMission(createCompletionCallback("暂停任务"));
    }

    private void resumeMission() {
        WaypointMissionManager.getInstance().resumeMission(createCompletionCallback("恢复任务"));
    }

    private void stopMission() {
        if (!hasMissionFile()) {
            showToast("当前没有任务文件");
            return;
        }
        WaypointMissionManager.getInstance().stopMission(getMissionId(), createCompletionCallback("停止任务"));
    }

    private CommonCallbacks.CompletionCallback createCompletionCallback(String actionName) {
        return new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> showToast(actionName + "成功"));
            }

            @Override
            public void onFailure(IDJIError error) {
                runOnUiThread(() -> showToast(actionName + "失败：" + getErrorMessage(error)));
            }
        };
    }

    private void setAddPointMode(boolean enabled) {
        if (enabled && editingRoute == null) {
            startNewRouteEdit();
        }
        addPointMode = enabled;
        addPointButton.setText(enabled ? "结束放置" : "放置航点");
        addPointButton.setAlpha(enabled ? 0.86f : 1f);
        if (enabled) {
            showWaypointPanel();
            applyMapClickListener();
            waypointHintText.setText("添加模式已开启：点击地图任意位置即可添加航点；长按已有航点可拖动调整。");
        } else {
            DJIMap map = mapWidget.getMap();
            if (map != null) {
                map.removeOnMapClickListener(addPointListener);
                map.removeOnMapLongClickListener(addPointLongClickListener);
            }
            waypointHintText.setText("点击“放置航点”后，在地图任意位置点击即可添加；长按航点 marker 可拖动更新坐标。");
        }
        updateEditButtonState();
    }

    private void applyMapClickListener() {
        DJIMap map = mapWidget.getMap();
        if (map != null) {
            map.removeOnMapClickListener(addPointListener);
            map.setOnMapClickListener(addPointListener);
            map.removeOnMapLongClickListener(addPointLongClickListener);
            map.setOnMapLongClickListener(addPointLongClickListener);
        }
    }

    private void clearMission() {
        RouteUiModel targetRoute = editingSourceRoute != null ? editingSourceRoute : getActiveRoute();
        if (targetRoute == null) {
            showToast("当前没有可清除的航线");
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("删除航线")
                .setMessage("确定要删除航线 " + targetRoute.name + " 吗？")
                .setPositiveButton("删除", (dialog, which) -> deleteCurrentRoute(targetRoute))
                .setNegativeButton("取消", null)
                .show();
    }

    private boolean onMarkerClick(DJIMarker marker) {
        if (marker != null && marker.getTag() instanceof InsertMarkerTag) {
            insertWaypointAtSegment(((InsertMarkerTag) marker.getTag()).segmentStartIndex);
            return true;
        }
        int index = getWaypointMarkerIndex(marker);
        if (!isValidWaypointIndex(index)) {
            return false;
        }
        selectWaypoint(index, true);
        showWaypointPanel();
        return true;
    }

    private void selectWaypoint(int index, boolean centerOnMap) {
        if (!isValidWaypointIndex(index)) {
            return;
        }
        selectedWaypointIndex = index;
        refreshMapOverlays();
        refreshWaypointList(true);
        waypointHintText.setText(centerOnMap
                ? "航点已选中，点击“定位到航点”可回中查看。"
                : "航点已选中，当前地图视角保持不变。");
    }

    private void applyWaypointDetailEdits() {
        if (editingRoute == null || updatingWaypointFields || !isValidWaypointIndex(selectedWaypointIndex)) {
            return;
        }
        EditableWaypoint waypoint = editWaypoints.get(selectedWaypointIndex);
        boolean changed = false;
        Double latitude = parseDouble(waypointLatitudeEdit.getText().toString());
        Double longitude = parseDouble(waypointLongitudeEdit.getText().toString());
        Double height = parseDouble(waypointHeightEdit.getText().toString());
        Double speed = parseDouble(waypointSpeedEdit.getText().toString());
        String action = waypointActionEdit.getText().toString().trim();
        if (latitude != null && latitude >= -90d && latitude <= 90d && waypoint.latitude != latitude) {
            waypoint.latitude = latitude;
            changed = true;
        }
        if (longitude != null && longitude >= -180d && longitude <= 180d && waypoint.longitude != longitude) {
            waypoint.longitude = longitude;
            changed = true;
        }
        if (height != null && height >= -500d && waypoint.height != height) {
            waypoint.height = height;
            changed = true;
        }
        if (speed != null && speed > 0d && waypoint.speed != speed) {
            waypoint.speed = speed;
            changed = true;
        }
        if (!TextUtils.equals(waypoint.action, action)) {
            waypoint.action = action;
            changed = true;
        }
        if (!changed) {
            return;
        }
        markEditingRouteDirty(false);
        refreshMapOverlays();
        refreshWaypointList(false);
        refreshRouteInfo();
    }

    private void markEditingRouteDirty(boolean collapseToSingleSegment) {
        if (editingRoute == null) {
            return;
        }
        currentMissionPath = "";
        selectedWaylineIds.clear();
        editingRoute.waypoints.clear();
        editingRoute.waypoints.addAll(editWaypoints);
        editingRoute.segments.clear();
        if (!editWaypoints.isEmpty()) {
            editingRoute.segments.add(new ArrayList<>(editWaypoints));
        }
        editingRoute.recalculate();
        updateSelectedWaylineStatus();
    }

    private void refreshMapOverlays() {
        clearMapOverlays();
        if (routeVisible) {
            for (RouteUiModel route : routeModels) {
                if (editingSourceRoute != null && route == editingSourceRoute) {
                    continue;
                }
                addRouteLines(route, route == currentRoute || route == editingSourceRoute);
            }
            if (editingRoute != null) {
                addRouteLines(editingRoute, true);
            }
        }
        RouteUiModel focusRoute = getActiveRoute();
        if (focusRoute == null) {
            return;
        }
        List<EditableWaypoint> focusWaypoints = getActiveWaypoints();
        for (int i = 0; i < focusWaypoints.size(); i++) {
            addWaypointMarker(focusWaypoints.get(i), i, editingRoute != null);
        }
    }

    private void clearMapOverlays() {
        for (DJIMarker marker : pointMarkers) {
            if (marker != null) {
                marker.remove();
            }
        }
        pointMarkers.clear();
        for (DJIMarker marker : distanceMarkers) {
            if (marker != null) {
                marker.remove();
            }
        }
        distanceMarkers.clear();
        for (DJIMarker marker : insertPointMarkers) {
            if (marker != null) {
                marker.remove();
            }
        }
        insertPointMarkers.clear();
        for (DJIPolyline line : routeLines) {
            if (line != null) {
                line.remove();
            }
        }
        routeLines.clear();
    }

    private void addWaypointMarker(EditableWaypoint waypoint, int index, boolean draggable) {
        DJIMap map = mapWidget.getMap();
        if (map == null) {
            return;
        }
        boolean selected = index == selectedWaypointIndex;
        DJIMarkerOptions markerOptions = new DJIMarkerOptions()
                .position(waypoint.toLatLng())
                .title("W" + (index + 1))
                .icon(createWaypointMarkerIcon(index + 1, selected))
                .draggable(draggable)
                .zIndex(selected ? 45 : 40)
                .setInfoWindowEnable(false);
        DJIMarker marker = map.addMarker(markerOptions);
        if (marker != null) {
            marker.setTag(new WaypointMarkerTag(index));
            marker.setDraggable(draggable);
            pointMarkers.add(marker);
        }
    }

    private void addRouteLines(RouteUiModel route, boolean showLabels) {
        List<List<EditableWaypoint>> segments = getSegments(route);
        for (List<EditableWaypoint> segment : segments) {
            List<DJILatLng> points = toLatLngList(segment);
            if (points.size() < 2) {
                continue;
            }
            addRouteLine(points, withAlpha(route.color, 0x66), 12f, 18f);
            addRouteLine(points, route.color, 6f, 20f);
            if (showLabels) {
                addDistanceLabels(points, editingRoute != null && route == editingRoute);
            }
        }
    }

    private List<List<EditableWaypoint>> getSegments(RouteUiModel route) {
        List<List<EditableWaypoint>> segments = new ArrayList<>();
        if (route != null && !route.segments.isEmpty()) {
            segments.addAll(route.segments);
        } else if (route != null && !route.waypoints.isEmpty()) {
            segments.add(new ArrayList<>(route.waypoints));
        }
        return segments;
    }

    private void addRouteLine(List<DJILatLng> points, int color, float width, float zIndex) {
        DJIMap map = mapWidget.getMap();
        if (map == null) {
            return;
        }
        DJIPolylineOptions lineOptions = new DJIPolylineOptions()
                .width(width)
                .color(color)
                .zIndex(zIndex)
                .addAll(points);
        DJIPolyline line = map.addPolyline(lineOptions);
        if (line != null) {
            routeLines.add(line);
        }
    }

    private void addDistanceLabels(List<DJILatLng> points, boolean showInsertButtons) {
        DJIMap map = mapWidget.getMap();
        if (map == null) {
            return;
        }
        for (int i = 0; i < points.size() - 1; i++) {
            DJILatLng start = points.get(i);
            DJILatLng end = points.get(i + 1);
            DJILatLng labelPoint = interpolate(start, end, showInsertButtons ? 0.58d : 0.5d);
            DJIMarkerOptions options = new DJIMarkerOptions()
                    .position(labelPoint)
                    .icon(DJIBitmapDescriptorFactory.fromView(createDistanceLabelView(formatDistance(distanceMeters(start, end)))))
                    .anchor(0.5f, 0.5f)
                    .zIndex(35)
                    .setInfoWindowEnable(false);
            DJIMarker marker = map.addMarker(options);
            if (marker != null) {
                distanceMarkers.add(marker);
            }
            if (showInsertButtons) {
                DJIMarkerOptions insertOptions = new DJIMarkerOptions()
                        .position(interpolate(start, end, 0.42d))
                        .icon(DJIBitmapDescriptorFactory.fromView(createInsertButtonView()))
                        .anchor(0.5f, 0.5f)
                        .zIndex(38)
                        .setInfoWindowEnable(false);
                DJIMarker insertMarker = map.addMarker(insertOptions);
                if (insertMarker != null) {
                    insertMarker.setTag(new InsertMarkerTag(i));
                    insertPointMarkers.add(insertMarker);
                }
            }
        }
    }

    private void refreshRouteList() {
        routeListView.removeAllViews();
        if (routeModels.isEmpty()) {
            routeListView.addView(createEmptyTextView("暂无导入航线"));
            return;
        }
        for (int i = 0; i < routeModels.size(); i++) {
            RouteUiModel route = routeModels.get(i);
            route.recalculate();
            LinearLayout item = new LinearLayout(activity);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setMinimumHeight(dp(58));
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setPadding(dp(12), dp(8), dp(8), dp(8));
            item.setBackground(createRoundedRect(route == currentRoute ? SELECTED_CARD_COLOR : CARD_COLOR,
                    route == currentRoute ? route.color : CARD_STROKE_COLOR,
                    dp(8)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(8);
            item.setLayoutParams(params);

            TextView info = new TextView(activity);
            info.setGravity(Gravity.CENTER_VERTICAL);
            info.setTextColor(route == currentRoute ? TEXT_PRIMARY : TEXT_SECONDARY);
            info.setTextSize(12f);
            info.setTypeface(Typeface.DEFAULT, route == currentRoute ? Typeface.BOLD : Typeface.NORMAL);
            info.setText(route.name + "\n" + route.waypointCount + " 点 · " + formatDistance(route.totalLengthMeters));
            LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            item.addView(info, infoParams);

            FrameLayout edit = new FrameLayout(activity);
            edit.setBackground(createOvalBackground(0x22007AFF, 0x66007AFF, dp(1)));
            ImageView editIcon = new ImageView(activity);
            editIcon.setImageResource(R.drawable.uxsdk_ic_edit_pencil);
            editIcon.setColorFilter(Color.WHITE);
            editIcon.setScaleType(ImageView.ScaleType.CENTER);
            FrameLayout.LayoutParams editIconParams = new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER);
            edit.addView(editIcon, editIconParams);
            LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(dp(40), dp(40));
            item.addView(edit, editParams);

            item.setOnClickListener(v -> selectRoute(route, true));
            item.setOnLongClickListener(v -> {
                confirmDeleteRoute(route);
                return true;
            });
            edit.setOnClickListener(v -> beginEditRoute(route));
            routeListView.addView(item);
        }
    }

    private void refreshRouteInfo() {
        RouteUiModel route = getActiveRoute();
        if (route == null) {
            updatingRouteName = true;
            routeNameEdit.setText("");
            routeNameEdit.setEnabled(false);
            updatingRouteName = false;
            totalLengthText.setText("总长度：--");
            waypointCountText.setText("航点数量：--");
            estimateTimeText.setText("预计飞行时间：--");
            colorPickerView.removeAllViews();
            selectedIdsText.setText("ID: --");
            updateStartButtonState();
            updateEditButtonState();
            return;
        }
        route.recalculate();
        updatingRouteName = true;
        if (!TextUtils.equals(routeNameEdit.getText().toString(), route.name)) {
            routeNameEdit.setText(route.name);
            routeNameEdit.setSelection(routeNameEdit.getText().length());
        }
        routeNameEdit.setEnabled(editingRoute != null);
        updatingRouteName = false;
        totalLengthText.setText("总长度：" + formatDistance(route.totalLengthMeters));
        waypointCountText.setText("航点数量：" + route.waypointCount);
        estimateTimeText.setText("预计飞行时间：" + formatDuration(route.estimatedSeconds));
        selectedIdsText.setText("ID: " + getRouteDisplayId(route));
        refreshColorPicker(route);
        updateStartButtonState();
        updateEditButtonState();
    }

    private void refreshColorPicker(RouteUiModel route) {
        colorPickerView.removeAllViews();
        if (route == null) {
            return;
        }
        for (int color : ROUTE_COLOR_OPTIONS) {
            TextView swatch = new TextView(activity);
            boolean selected = (route.color & 0x00FFFFFF) == (color & 0x00FFFFFF);
            swatch.setGravity(Gravity.CENTER);
            swatch.setText(selected ? "✓" : "");
            swatch.setTextColor(color == Color.WHITE ? ROUTE_COLOR : Color.WHITE);
            swatch.setTextSize(15f);
            swatch.setTypeface(Typeface.DEFAULT_BOLD);
            swatch.setBackground(createColorSwatchBackground(color, selected));
            swatch.setAlpha(editingRoute == null ? 0.55f : 1f);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(32), dp(32));
            params.rightMargin = dp(12);
            colorPickerView.addView(swatch, params);
            swatch.setOnClickListener(v -> {
                if (editingRoute == null) {
                    showToast("请先进入航线编辑模式再修改颜色");
                    return;
                }
                editingRoute.color = color;
                currentMissionPath = "";
                selectedWaylineIds.clear();
                updateSelectedWaylineStatus();
                refreshColorPicker(editingRoute);
                refreshRouteList();
                refreshMapOverlays();
            });
        }
    }

    private void refreshWaypointList(boolean updateDetailFields) {
        waypointListView.removeAllViews();
        List<EditableWaypoint> activeWaypoints = getActiveWaypoints();
        if (activeWaypoints.isEmpty()) {
            waypointListView.addView(createEmptyTextView("暂无航点"));
            waypointDetailCard.setVisibility(View.GONE);
            return;
        }
        for (int i = 0; i < activeWaypoints.size(); i++) {
            EditableWaypoint waypoint = activeWaypoints.get(i);
            boolean selected = i == selectedWaypointIndex;
            TextView item = new TextView(activity);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setMinHeight(dp(58));
            item.setPadding(dp(12), dp(8), dp(12), dp(8));
            item.setTextColor(selected ? TEXT_PRIMARY : TEXT_SECONDARY);
            item.setTextSize(12f);
            item.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            item.setBackground(createRoundedRect(selected ? SELECTED_CARD_COLOR : CARD_COLOR,
                    selected ? ROUTE_COLOR : CARD_STROKE_COLOR,
                    dp(8)));
            item.setText(String.format(Locale.US,
                    "%02d  %s, %s\nH %.1fm · V %.1fm/s",
                    i + 1,
                    formatCoordinateShort(waypoint.latitude),
                    formatCoordinateShort(waypoint.longitude),
                    waypoint.height,
                    waypoint.speed));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(8);
            item.setLayoutParams(params);
            final int index = i;
            item.setOnClickListener(v -> selectWaypoint(index, false));
            waypointListView.addView(item);
        }
        if (updateDetailFields) {
            updateWaypointDetailFields();
        }
    }

    private void updateWaypointDetailFields() {
        if (!isValidWaypointIndex(selectedWaypointIndex)) {
            waypointDetailCard.setVisibility(View.GONE);
            return;
        }
        EditableWaypoint waypoint = getActiveWaypoints().get(selectedWaypointIndex);
        waypointDetailCard.setVisibility(View.VISIBLE);
        updatingWaypointFields = true;
        waypointDetailTitle.setText("航点 " + (selectedWaypointIndex + 1) + " 设置");
        waypointLatitudeEdit.setText(formatCoordinate(waypoint.latitude));
        waypointLongitudeEdit.setText(formatCoordinate(waypoint.longitude));
        waypointHeightEdit.setText(formatNumber(waypoint.height));
        waypointSpeedEdit.setText(formatNumber(waypoint.speed));
        waypointActionEdit.setText(waypoint.action);
        boolean editing = editingRoute != null;
        waypointLatitudeEdit.setEnabled(editing);
        waypointLongitudeEdit.setEnabled(editing);
        waypointHeightEdit.setEnabled(editing);
        waypointSpeedEdit.setEnabled(editing);
        waypointActionEdit.setEnabled(editing);
        updatingWaypointFields = false;
    }

    private TextView createEmptyTextView(String text) {
        TextView emptyText = new TextView(activity);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setMinHeight(dp(48));
        emptyText.setPadding(dp(12), dp(10), dp(12), dp(10));
        emptyText.setText(text);
        emptyText.setTextColor(TEXT_SECONDARY);
        emptyText.setTextSize(12f);
        emptyText.setBackground(createRoundedRect(CARD_COLOR, CARD_STROKE_COLOR, dp(8)));
        return emptyText;
    }

    private View createDistanceLabelView(String text) {
        TextView label = new TextView(activity);
        label.setText(text);
        label.setTextColor(Color.WHITE);
        label.setTextSize(11f);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setPadding(dp(8), dp(3), dp(8), dp(3));
        label.setBackground(createRoundedRect(0xCC1C1C1E, 0x663A3A3C, dp(6)));
        return label;
    }

    private View createInsertButtonView() {
        FrameLayout touchTarget = new FrameLayout(activity);
        touchTarget.setLayoutParams(new ViewGroup.LayoutParams(dp(40), dp(40)));

        TextView button = new TextView(activity);
        button.setText("+");
        button.setIncludeFontPadding(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14f);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(createOvalBackground(0xCC1976D2, 0xFFFFFFFF, dp(1)));
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER);
        touchTarget.addView(button, buttonParams);
        return touchTarget;
    }

    private dji.v5.ux.mapkit.core.models.DJIBitmapDescriptor createWaypointMarkerIcon(int index, boolean selected) {
        int outerSize = dp(selected ? 42 : 32);
        int dotSize = dp(selected ? 34 : 26);
        FrameLayout container = new FrameLayout(activity);
        container.setLayoutParams(new ViewGroup.LayoutParams(outerSize, outerSize));
        if (selected) {
            GradientDrawable halo = new GradientDrawable();
            halo.setShape(GradientDrawable.OVAL);
            halo.setColor(0x33007AFF);
            container.setBackground(halo);
        }
        TextView dot = new TextView(activity);
        dot.setGravity(Gravity.CENTER);
        dot.setText(String.valueOf(index));
        dot.setTextColor(Color.WHITE);
        dot.setTextSize(selected ? 12f : 10f);
        dot.setTypeface(Typeface.DEFAULT_BOLD);
        RouteUiModel focusRoute = getActiveRoute();
        int markerColor = focusRoute == null ? ROUTE_COLOR : focusRoute.color;
        GradientDrawable dotBackground = new GradientDrawable();
        dotBackground.setShape(GradientDrawable.OVAL);
        dotBackground.setColor(markerColor);
        dotBackground.setStroke(dp(selected ? 3 : 2), selected ? Color.WHITE : 0xCCFFFFFF);
        dot.setBackground(dotBackground);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dotSize, dotSize, Gravity.CENTER);
        container.addView(dot, params);
        return DJIBitmapDescriptorFactory.fromView(container);
    }

    private GradientDrawable createRoundedRect(int color, int strokeColor, int radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private GradientDrawable createColorSwatchBackground(int color, boolean selected) {
        return createOvalBackground(color, selected ? Color.WHITE : 0x663A3A3C, dp(selected ? 3 : 1));
    }

    private GradientDrawable createOvalBackground(int color, int strokeColor, int strokeWidthPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        drawable.setStroke(strokeWidthPx, strokeColor);
        return drawable;
    }

    private List<DJILatLng> toLatLngList(List<EditableWaypoint> waypoints) {
        List<DJILatLng> points = new ArrayList<>(waypoints.size());
        for (EditableWaypoint waypoint : waypoints) {
            points.add(waypoint.toLatLng());
        }
        return points;
    }

    private void centerRoute(RouteUiModel route) {
        DJIMap map = mapWidget.getMap();
        if (map == null || route == null || route.waypoints.isEmpty()) {
            return;
        }
        List<DJILatLng> points = toLatLngList(route.waypoints);
        if (points.size() == 1) {
            centerPoint(points.get(0));
            return;
        }
        try {
            int panelPadding = panel.getWidth() > 0 ? panel.getWidth() + dp(56) : dp(360);
            map.animateCamera(DJICameraUpdateFactory.newLatLngBounds(
                    DJILatLngBounds.fromLatLngs(points),
                    dp(56),
                    dp(56),
                    dp(56),
                    panelPadding,
                    16));
        } catch (Exception ignored) {
            centerPoint(points.get(0));
        }
    }

    private void centerWaypoint(int index) {
        if (!isValidWaypointIndex(index)) {
            return;
        }
        centerPoint(getActiveWaypoints().get(index).toLatLng());
    }

    private void centerSelectedWaypoint() {
        if (!isValidWaypointIndex(selectedWaypointIndex)) {
            showToast("请先选择一个航点");
            return;
        }
        centerWaypoint(selectedWaypointIndex);
    }

    private void centerPoint(DJILatLng point) {
        DJIMap map = mapWidget.getMap();
        if (map == null || point == null) {
            return;
        }
        float zoom = 17f;
        DJICameraPosition currentPosition = map.getCameraPosition();
        if (currentPosition != null && currentPosition.zoom > 0f) {
            zoom = currentPosition.zoom;
        }
        map.moveCamera(DJICameraUpdateFactory.newCameraPosition(DJICameraPosition.fromLatLngZoom(point, zoom)));
    }

    private void insertWaypointAtSegment(int segmentStartIndex) {
        if (editingRoute == null || segmentStartIndex < 0 || segmentStartIndex >= editWaypoints.size() - 1) {
            return;
        }
        EditableWaypoint start = editWaypoints.get(segmentStartIndex);
        EditableWaypoint end = editWaypoints.get(segmentStartIndex + 1);
        EditableWaypoint waypoint = new EditableWaypoint(
                (start.latitude + end.latitude) / 2d,
                (start.longitude + end.longitude) / 2d,
                (start.height + end.height) / 2d,
                (start.speed + end.speed) / 2d,
                "");
        int insertIndex = segmentStartIndex + 1;
        editWaypoints.add(insertIndex, waypoint);
        selectedWaypointIndex = insertIndex;
        markEditingRouteDirty(true);
        refreshMapOverlays();
        refreshRouteList();
        refreshRouteInfo();
        refreshWaypointList(true);
        showWaypointPanel();
        openPanel(false);
        waypointHintText.setText("已在线段中点插入航点，可立即编辑高度和速度。");
    }

    private void updateSelectedWaylineStatus() {
        selectedIdsText.setText("ID: " + getRouteDisplayId(getActiveRoute()));
        updateStartButtonState();
    }

    private String getRouteDisplayId(@Nullable RouteUiModel route) {
        if (route == null) {
            return "--";
        }
        if (!TextUtils.isEmpty(route.path)) {
            return new File(route.path).getName();
        }
        return TextUtils.isEmpty(route.name) ? "--" : route.name;
    }

    private void updateStartButtonState() {
        boolean enabled = hasMissionFile();
        startButton.setEnabled(enabled);
        startButton.setAlpha(enabled ? 1f : 0.45f);
    }

    private void updateEditButtonState() {
        boolean editing = editingRoute != null;
        saveEditButton.setText(editingSourceRoute == null ? "保存航迹" : "保存修改");
        saveEditButton.setEnabled(editing);
        saveEditButton.setAlpha(editing ? 1f : 0.45f);
        discardEditButton.setEnabled(editing);
        discardEditButton.setAlpha(editing ? 1f : 0.45f);
        deletePointButton.setEnabled(editing);
        deletePointButton.setAlpha(editing ? 1f : 0.45f);
        boolean hasActiveRoute = getActiveRoute() != null;
        clearRouteButton.setEnabled(hasActiveRoute);
        clearRouteButton.setAlpha(hasActiveRoute ? 1f : 0.45f);
    }

    private RouteUiModel getActiveRoute() {
        return editingRoute != null ? editingRoute : currentRoute;
    }

    private List<EditableWaypoint> getActiveWaypoints() {
        RouteUiModel activeRoute = getActiveRoute();
        if (activeRoute == null) {
            return new ArrayList<>();
        }
        return activeRoute.waypoints;
    }

    private void beginEditRoute(RouteUiModel route) {
        if (route == null) {
            return;
        }
        if (editingRoute != null) {
            showToast("请先保存或放弃当前编辑");
            return;
        }
        editingSourceRoute = route;
        editingRoute = route.copy();
        currentRoute = route;
        editWaypoints.clear();
        editWaypoints.addAll(editingRoute.waypoints);
        selectedWaypointIndex = -1;
        currentMissionPath = "";
        selectedWaylineIds.clear();
        updateSelectedWaylineStatus();
        showWaypointPanel();
        openPanel(false);
        refreshRouteInfo();
        refreshWaypointList(true);
        refreshMapOverlays();
        statusText.setText("正在编辑：" + route.name);
    }

    private void discardEdit() {
        if (editingRoute == null) {
            return;
        }
        setAddPointMode(false);
        editingRoute = null;
        editingSourceRoute = null;
        editWaypoints.clear();
        selectedWaypointIndex = -1;
        if (currentRoute != null) {
            currentMissionPath = currentRoute.path;
        }
        updateSelectedWaylineStatus();
        refreshRouteInfo();
        refreshWaypointList(true);
        refreshMapOverlays();
        statusText.setText("已放弃编辑");
    }

    private void confirmDeleteRoute(RouteUiModel route) {
        new AlertDialog.Builder(activity)
                .setTitle("删除航线")
                .setMessage("确定删除“" + route.name + "”？")
                .setPositiveButton("删除", (dialog, which) -> deleteRoute(route))
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteCurrentRoute(RouteUiModel targetRoute) {
        if (targetRoute == null) {
            return;
        }
        setAddPointMode(false);
        if (editingRoute != null && editingSourceRoute == null && targetRoute == editingRoute) {
            editingRoute = null;
            editWaypoints.clear();
            currentRoute = routeModels.isEmpty() ? null : routeModels.get(0);
            currentMissionPath = currentRoute == null ? "" : currentRoute.path;
            selectedWaypointIndex = -1;
            selectedWaylineIds.clear();
            updateSelectedWaylineStatus();
            refreshRouteList();
            refreshRouteInfo();
            refreshWaypointList(true);
            refreshMapOverlays();
            statusText.setText("已删除当前未保存航线");
            return;
        }
        deleteRoute(targetRoute);
        statusText.setText("已删除航线：" + targetRoute.name);
    }

    private void deleteRoute(RouteUiModel route) {
        if (route == null) {
            return;
        }
        if (editingSourceRoute == route || editingRoute == route) {
            editingRoute = null;
            editingSourceRoute = null;
            editWaypoints.clear();
            setAddPointMode(false);
        }
        if (!TextUtils.isEmpty(route.path)) {
            File file = new File(route.path);
            if (file.exists() && !file.delete()) {
                showToast("航线文件删除失败");
            }
        }
        routeModels.remove(route);
        if (currentRoute == route) {
            currentRoute = routeModels.isEmpty() ? null : routeModels.get(0);
            currentMissionPath = currentRoute == null ? "" : currentRoute.path;
            selectedWaypointIndex = -1;
        }
        selectedWaylineIds.clear();
        updateSelectedWaylineStatus();
        refreshRouteList();
        refreshRouteInfo();
        refreshWaypointList(true);
        refreshMapOverlays();
    }

    private File getSaveFileForEditingRoute(String routeName) {
        if (editingSourceRoute != null && !TextUtils.isEmpty(editingSourceRoute.path)) {
            return new File(editingSourceRoute.path);
        }
        return getAvailableKmzFile(routeName);
    }

    private File getAvailableKmzFile(String routeName) {
        String baseName = normalizeRouteFileBaseName(routeName);
        File missionDir = getMissionDir();
        File candidate = new File(missionDir, baseName + ".kmz");
        int duplicateIndex = 1;
        while (candidate.exists()) {
            candidate = new File(missionDir, baseName + "_" + duplicateIndex + ".kmz");
            duplicateIndex++;
        }
        return candidate;
    }

    private String normalizeRouteFileBaseName(String routeName) {
        String name = routeName == null ? "" : routeName.trim();
        name = stripKmzExtension(name);
        name = sanitizeFileName(name);
        return TextUtils.isEmpty(name) ? defaultRouteName() : name;
    }

    private List<EditableWaypoint> copyWaypoints(List<EditableWaypoint> source) {
        List<EditableWaypoint> copy = new ArrayList<>(source.size());
        for (EditableWaypoint waypoint : source) {
            copy.add(waypoint.copy());
        }
        return copy;
    }

    private void copyRoute(RouteUiModel fromRoute, RouteUiModel toRoute) {
        toRoute.name = fromRoute.name;
        toRoute.path = fromRoute.path;
        toRoute.color = fromRoute.color;
        toRoute.waypoints.clear();
        toRoute.waypoints.addAll(copyWaypoints(fromRoute.waypoints));
        toRoute.segments.clear();
        for (List<EditableWaypoint> segment : fromRoute.segments) {
            toRoute.segments.add(copyWaypoints(segment));
        }
        toRoute.recalculate();
    }

    private int getInsertIndex(EditableWaypoint waypoint) {
        if (editWaypoints.size() < 2) {
            return editWaypoints.size();
        }
        DJILatLng target = waypoint.toLatLng();
        double nearestSegmentMeters = Double.MAX_VALUE;
        int nearestSegmentIndex = editWaypoints.size() - 1;
        for (int i = 0; i < editWaypoints.size() - 1; i++) {
            double distance = distanceToSegmentMeters(
                    target,
                    editWaypoints.get(i).toLatLng(),
                    editWaypoints.get(i + 1).toLatLng());
            if (distance < nearestSegmentMeters) {
                nearestSegmentMeters = distance;
                nearestSegmentIndex = i + 1;
            }
        }
        double distanceToLast = distanceMeters(target, editWaypoints.get(editWaypoints.size() - 1).toLatLng());
        return nearestSegmentMeters < distanceToLast ? nearestSegmentIndex : editWaypoints.size();
    }

    private boolean isValidWaypointIndex(int index) {
        return index >= 0 && index < getActiveWaypoints().size();
    }

    private int getWaypointMarkerIndex(DJIMarker marker) {
        if (marker == null || !(marker.getTag() instanceof WaypointMarkerTag)) {
            return -1;
        }
        return ((WaypointMarkerTag) marker.getTag()).index;
    }

    private WaylineMission createWaylineMission() {
        WaylineMission mission = new WaylineMission();
        double now = (double) System.currentTimeMillis();
        mission.setCreateTime(now);
        mission.setUpdateTime(now);
        return mission;
    }

    private WaylineMissionConfig createMissionConfig() {
        WaylineMissionConfig config = new WaylineMissionConfig();
        config.setFlyToWaylineMode(WaylineFlyToWaylineMode.SAFELY);
        config.setFinishAction(WaylineFinishedAction.GO_HOME);
        config.setDroneInfo(new WaylineDroneInfo());
        config.setSecurityTakeOffHeight(20d);
        config.setIsSecurityTakeOffHeightSet(true);
        config.setExitOnRCLostBehavior(WaylineExitOnRCLostBehavior.EXCUTE_RC_LOST_ACTION);
        config.setExitOnRCLostType(WaylineExitOnRCLostAction.GO_BACK);
        config.setGlobalTransitionalSpeed(10d);
        config.setPayloadInfo(new ArrayList<WaylinePayloadInfo>());
        return config;
    }

    private Template createTemplate(List<EditableWaypoint> waypoints) {
        Template template = new Template();
        template.setWaypointInfo(createTemplateWaypointInfo(waypoints));
        template.setCoordinateParam(createCoordinateParam());
        template.setUseGlobalTransitionalSpeed(true);
        template.setAutoFlightSpeed(getAverageSpeed(waypoints));
        template.setPayloadParam(new ArrayList<>());
        return template;
    }

    private WaylineCoordinateParam createCoordinateParam() {
        WaylineCoordinateParam coordinateParam = new WaylineCoordinateParam();
        coordinateParam.setCoordinateMode(WaylineCoordinateMode.WGS84);
        coordinateParam.setPositioningType(WaylinePositioningType.GPS);
        coordinateParam.setIsWaylinePositioningTypeSet(true);
        coordinateParam.setAltitudeMode(WaylineAltitudeMode.RELATIVE_TO_START_POINT);
        return coordinateParam;
    }

    private WaylineTemplateWaypointInfo createTemplateWaypointInfo(List<EditableWaypoint> points) {
        List<WaylineWaypoint> waypoints = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            EditableWaypoint point = points.get(i);
            WaylineWaypoint waypoint = new WaylineWaypoint();
            waypoint.setWaypointIndex(i);
            waypoint.setLocation(new WaylineLocationCoordinate2D(point.latitude, point.longitude));
            waypoint.setHeight(point.height);
            waypoint.setEllipsoidHeight(point.height);
            waypoint.setSpeed(point.speed);
            waypoint.setUseGlobalTurnParam(true);
            waypoint.setUseGlobalYawParam(true);
            waypoints.add(waypoint);
        }

        WaylineTemplateWaypointInfo waypointInfo = new WaylineTemplateWaypointInfo();
        waypointInfo.setWaypoints(waypoints);
        waypointInfo.setActionGroups(new ArrayList<>());
        waypointInfo.setGlobalFlightHeight(DEFAULT_WAYPOINT_HEIGHT);
        waypointInfo.setIsGlobalFlightHeightSet(true);
        waypointInfo.setGlobalTurnMode(WaylineWaypointTurnMode.TO_POINT_AND_STOP_WITH_DISCONTINUITY_CURVATURE);
        waypointInfo.setUseStraightLine(true);
        waypointInfo.setIsTemplateGlobalTurnModeSet(true);
        WaylineWaypointYawParam yawParam = new WaylineWaypointYawParam();
        yawParam.setYawMode(WaylineWaypointYawMode.FOLLOW_WAYLINE);
        EditableWaypoint firstPoint = points.get(0);
        yawParam.setPoiLocation(new WaylineLocationCoordinate3D(
                firstPoint.latitude,
                firstPoint.longitude,
                firstPoint.height));
        waypointInfo.setGlobalYawParam(yawParam);
        waypointInfo.setIsTemplateGlobalYawParamSet(true);
        waypointInfo.setPitchMode(WaylineWaypointPitchMode.USE_POINT_SETTING);
        return waypointInfo;
    }

    private boolean hasMissionFile() {
        return !TextUtils.isEmpty(currentMissionPath) && new File(currentMissionPath).exists();
    }

    private String getMissionId() {
        String name = new File(currentMissionPath).getName();
        String lowerName = name.toLowerCase(Locale.US);
        if (lowerName.endsWith(".kmz")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }

    private File getMissionDir() {
        String dateFolder = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        File publicDocuments = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File publicMissionDir = new File(new File(publicDocuments, WAYPOINT_DOCUMENT_ROOT), dateFolder);
        if (ensureDirectory(publicMissionDir)) {
            return publicMissionDir;
        }

        File appDocuments = activity.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (appDocuments == null) {
            appDocuments = new File(activity.getFilesDir(), Environment.DIRECTORY_DOCUMENTS);
        }
        File appMissionDir = new File(new File(appDocuments, WAYPOINT_DOCUMENT_ROOT), dateFolder);
        ensureDirectory(appMissionDir);
        return appMissionDir;
    }

    private boolean ensureDirectory(File directory) {
        return directory != null && (directory.exists() ? directory.isDirectory() : directory.mkdirs());
    }

    private String getDisplayName(Uri uri) {
        ContentResolver resolver = activity.getContentResolver();
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
            // Fall through to URI path parsing.
        }
        String path = uri.getPath();
        if (path == null) {
            return "";
        }
        int slashIndex = path.lastIndexOf('/');
        return slashIndex >= 0 ? path.substring(slashIndex + 1) : path;
    }

    private String sanitizeFileName(String filename) {
        return filename.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String defaultRouteName() {
        return DEFAULT_ROUTE_NAME_PREFIX + "_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    private String stripKmzExtension(String filename) {
        if (filename.toLowerCase(Locale.US).endsWith(".kmz")) {
            return filename.substring(0, filename.length() - 4);
        }
        return filename;
    }

    private String getErrorMessage(IDJIError error) {
        if (error == null) {
            return "";
        }
        if (!TextUtils.isEmpty(error.description())) {
            return error.description();
        }
        return error.errorCode();
    }

    private Double parseDouble(String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String formatCoordinate(double value) {
        return String.format(Locale.US, "%.7f", value);
    }

    private String formatCoordinateShort(double value) {
        return String.format(Locale.US, "%.5f", value);
    }

    private String formatNumber(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private String formatDistance(double meters) {
        if (meters >= 1000d) {
            return String.format(Locale.US, "%.2fkm", meters / 1000d);
        }
        return String.format(Locale.US, "%.1fm", meters);
    }

    private String formatDuration(double seconds) {
        if (seconds <= 0d || Double.isNaN(seconds) || Double.isInfinite(seconds)) {
            return "--";
        }
        int totalSeconds = (int) Math.round(seconds);
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int remainSeconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%dh %02dmin", hours, minutes);
        }
        if (minutes > 0) {
            return String.format(Locale.US, "%dmin %02ds", minutes, remainSeconds);
        }
        return remainSeconds + "s";
    }

    private DJILatLng interpolate(DJILatLng start, DJILatLng end, double ratio) {
        return new DJILatLng(
                start.getLatitude() + (end.getLatitude() - start.getLatitude()) * ratio,
                start.getLongitude() + (end.getLongitude() - start.getLongitude()) * ratio);
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private String colorToHex(int color) {
        return String.format(Locale.US, "#%06X", color & 0x00FFFFFF);
    }

    private int parseRouteColor(String colorValue) {
        if (TextUtils.isEmpty(colorValue)) {
            return ROUTE_COLOR;
        }
        try {
            return Color.parseColor(colorValue);
        } catch (IllegalArgumentException ignored) {
            return ROUTE_COLOR;
        }
    }

    private void applyRouteMetadata(File file, RouteUiModel route) {
        if (file == null || route == null || !file.exists()) {
            return;
        }
        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (ROUTE_METADATA_ENTRY.equals(entry.getName())) {
                    JSONObject metadata = new JSONObject(new String(readZipEntry(zipInputStream), StandardCharsets.UTF_8));
                    String routeName = metadata.optString("name", "");
                    if (!TextUtils.isEmpty(routeName)) {
                        route.name = routeName;
                    }
                    route.color = parseRouteColor(metadata.optString("color", colorToHex(ROUTE_COLOR)));
                    return;
                }
                zipInputStream.closeEntry();
            }
        } catch (Exception ignored) {
            // Older KMZ files do not contain UI metadata. Keep the parsed route defaults.
        }
    }

    private void writeRouteMetadata(File kmzFile, RouteUiModel route) throws IOException {
        if (kmzFile == null || route == null || !kmzFile.exists()) {
            return;
        }
        JSONObject metadata = new JSONObject();
        try {
            metadata.put("name", route.name);
            metadata.put("color", colorToHex(route.color));
        } catch (Exception e) {
            throw new IOException("无法写入航线元数据", e);
        }

        File tempFile = new File(kmzFile.getParentFile(), kmzFile.getName() + ".tmp");
        byte[] buffer = new byte[8192];
        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(kmzFile));
             ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(tempFile))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (ROUTE_METADATA_ENTRY.equals(entry.getName())) {
                    zipInputStream.closeEntry();
                    continue;
                }
                ZipEntry copiedEntry = new ZipEntry(entry.getName());
                if (entry.getTime() >= 0L) {
                    copiedEntry.setTime(entry.getTime());
                }
                zipOutputStream.putNextEntry(copiedEntry);
                if (!entry.isDirectory()) {
                    int len;
                    while ((len = zipInputStream.read(buffer)) > 0) {
                        zipOutputStream.write(buffer, 0, len);
                    }
                }
                zipOutputStream.closeEntry();
                zipInputStream.closeEntry();
            }

            ZipEntry metadataEntry = new ZipEntry(ROUTE_METADATA_ENTRY);
            metadataEntry.setTime(System.currentTimeMillis());
            zipOutputStream.putNextEntry(metadataEntry);
            zipOutputStream.write(metadata.toString().getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
        }
        try (FileInputStream inputStream = new FileInputStream(tempFile);
             FileOutputStream outputStream = new FileOutputStream(kmzFile, false)) {
            int len;
            while ((len = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, len);
            }
        } finally {
            if (tempFile.exists() && !tempFile.delete()) {
                // Best effort cleanup.
            }
        }
    }

    private byte[] readZipEntry(ZipInputStream zipInputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = zipInputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, len);
        }
        return outputStream.toByteArray();
    }

    private double getAverageSpeed(List<EditableWaypoint> waypoints) {
        double speedSum = 0d;
        int count = 0;
        for (EditableWaypoint waypoint : waypoints) {
            if (waypoint.speed > 0d) {
                speedSum += waypoint.speed;
                count++;
            }
        }
        return count == 0 ? DEFAULT_WAYPOINT_SPEED : speedSum / count;
    }

    private double distanceMeters(DJILatLng start, DJILatLng end) {
        double earthRadius = 6371000d;
        double startLat = Math.toRadians(start.getLatitude());
        double endLat = Math.toRadians(end.getLatitude());
        double deltaLat = Math.toRadians(end.getLatitude() - start.getLatitude());
        double deltaLng = Math.toRadians(end.getLongitude() - start.getLongitude());
        double a = Math.sin(deltaLat / 2d) * Math.sin(deltaLat / 2d)
                + Math.cos(startLat) * Math.cos(endLat)
                * Math.sin(deltaLng / 2d) * Math.sin(deltaLng / 2d);
        double c = 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
        return earthRadius * c;
    }

    private double distanceToSegmentMeters(DJILatLng point, DJILatLng start, DJILatLng end) {
        double meanLat = Math.toRadians((start.getLatitude() + end.getLatitude() + point.getLatitude()) / 3d);
        double metersPerDegreeLat = 111320d;
        double metersPerDegreeLng = Math.cos(meanLat) * 111320d;
        double px = (point.getLongitude() - start.getLongitude()) * metersPerDegreeLng;
        double py = (point.getLatitude() - start.getLatitude()) * metersPerDegreeLat;
        double ex = (end.getLongitude() - start.getLongitude()) * metersPerDegreeLng;
        double ey = (end.getLatitude() - start.getLatitude()) * metersPerDegreeLat;
        double lengthSquared = ex * ex + ey * ey;
        if (lengthSquared <= 0d) {
            return Math.sqrt(px * px + py * py);
        }
        double t = Math.max(0d, Math.min(1d, (px * ex + py * ey) / lengthSquared));
        double dx = px - t * ex;
        double dy = py - t * ey;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private String timeSuffix() {
        return String.valueOf(System.currentTimeMillis()).substring(6);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private void showToast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    private void runOnUiThread(Runnable runnable) {
        activity.runOnUiThread(runnable);
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // No-op.
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // No-op.
        }
    }

    private static class EditableWaypoint {
        double latitude;
        double longitude;
        double height;
        double speed;
        String action;

        EditableWaypoint(double latitude, double longitude, double height, double speed, String action) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.height = height;
            this.speed = speed;
            this.action = action;
        }

        DJILatLng toLatLng() {
            return new DJILatLng(latitude, longitude);
        }

        EditableWaypoint copy() {
            return new EditableWaypoint(latitude, longitude, height, speed, action);
        }
    }

    private class RouteUiModel {
        String name;
        String path;
        final List<EditableWaypoint> waypoints = new ArrayList<>();
        final List<List<EditableWaypoint>> segments = new ArrayList<>();
        double totalLengthMeters;
        double estimatedSeconds;
        int waypointCount;
        int color = ROUTE_COLOR;

        RouteUiModel(String name, String path) {
            this.name = TextUtils.isEmpty(name) ? "未命名航线" : name;
            this.path = path;
        }

        void recalculate() {
            waypointCount = waypoints.size();
            totalLengthMeters = 0d;
            List<List<EditableWaypoint>> sourceSegments = segments.isEmpty()
                    ? getFallbackSegments()
                    : segments;
            for (List<EditableWaypoint> segment : sourceSegments) {
                for (int i = 0; i < segment.size() - 1; i++) {
                    totalLengthMeters += distanceMeters(segment.get(i).toLatLng(), segment.get(i + 1).toLatLng());
                }
            }
            double averageSpeed = getAverageSpeed(waypoints);
            estimatedSeconds = averageSpeed > 0d ? totalLengthMeters / averageSpeed : 0d;
        }

        private List<List<EditableWaypoint>> getFallbackSegments() {
            List<List<EditableWaypoint>> fallback = new ArrayList<>();
            if (!waypoints.isEmpty()) {
                fallback.add(waypoints);
            }
            return fallback;
        }

        RouteUiModel copy() {
            RouteUiModel copy = new RouteUiModel(name, path);
            copy.color = color;
            copy.waypoints.addAll(copyWaypoints(waypoints));
            for (List<EditableWaypoint> segment : segments) {
                copy.segments.add(copyWaypoints(segment));
            }
            copy.recalculate();
            return copy;
        }
    }

    private static class WaypointMarkerTag {
        final int index;

        WaypointMarkerTag(int index) {
            this.index = index;
        }
    }

    private static class InsertMarkerTag {
        final int segmentStartIndex;

        InsertMarkerTag(int segmentStartIndex) {
            this.segmentStartIndex = segmentStartIndex;
        }
    }
}
