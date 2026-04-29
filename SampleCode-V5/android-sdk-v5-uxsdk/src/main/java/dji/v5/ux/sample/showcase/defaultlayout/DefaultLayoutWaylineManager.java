package dji.v5.ux.sample.showcase.defaultlayout;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.dji.wpmzsdk.common.data.Template;
import com.dji.wpmzsdk.manager.WPMZManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
import dji.v5.ux.mapkit.core.maps.DJIMap;
import dji.v5.ux.mapkit.core.models.DJILatLng;
import dji.v5.ux.mapkit.core.models.annotations.DJIMarker;
import dji.v5.ux.mapkit.core.models.annotations.DJIMarkerOptions;
import dji.v5.ux.mapkit.core.models.annotations.DJIPolyline;
import dji.v5.ux.mapkit.core.models.annotations.DJIPolylineOptions;

class DefaultLayoutWaylineManager {

    static final int REQUEST_IMPORT_KMZ = 5107;

    private static final double DEFAULT_WAYPOINT_HEIGHT = 100d;
    private static final double DEFAULT_WAYPOINT_SPEED = 5d;
    private static final String GENERATED_MISSION_PREFIX = "amap_wayline_";

    private final AppCompatActivity activity;
    private final MapWidget mapWidget;
    private final View panel;
    private final TextView statusText;
    private final TextView selectedIdsText;
    private final TextView executeStateText;
    private final TextView executeInfoText;
    private final Button addPointButton;
    private final List<DJILatLng> editPoints = new ArrayList<>();
    private final List<DJIMarker> pointMarkers = new ArrayList<>();
    private final List<DJIPolyline> routeLines = new ArrayList<>();
    private final List<Integer> selectedWaylineIds = new ArrayList<>();

    private String currentMissionPath = "";
    private boolean addPointMode = false;

    private final DJIMap.OnMapClickListener addPointListener = this::addWaypointFromMap;
    private final WaypointMissionExecuteStateListener missionStateListener;
    private final WaylineExecutingInfoListener waylineExecutingInfoListener;
    private final WaypointActionListener waypointActionListener;

    DefaultLayoutWaylineManager(AppCompatActivity activity, MapWidget mapWidget, View rootView) {
        this.activity = activity;
        this.mapWidget = mapWidget;
        panel = rootView.findViewById(R.id.uxsdk_wayline_panel);
        statusText = rootView.findViewById(R.id.uxsdk_wayline_status);
        selectedIdsText = rootView.findViewById(R.id.uxsdk_wayline_selected_ids);
        executeStateText = rootView.findViewById(R.id.uxsdk_wayline_execute_state);
        executeInfoText = rootView.findViewById(R.id.uxsdk_wayline_execute_info);
        addPointButton = rootView.findViewById(R.id.uxsdk_wayline_add_point);
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
        rootView.findViewById(R.id.uxsdk_wayline_save_kmz).setOnClickListener(v -> saveCurrentEditMission(true));
        rootView.findViewById(R.id.uxsdk_wayline_clear).setOnClickListener(v -> clearMission());
        rootView.findViewById(R.id.uxsdk_wayline_upload).setOnClickListener(v -> uploadCurrentMission());
        rootView.findViewById(R.id.uxsdk_wayline_select_ids).setOnClickListener(v -> showWaylineIdDialog());
        rootView.findViewById(R.id.uxsdk_wayline_start).setOnClickListener(v -> startMission());
        rootView.findViewById(R.id.uxsdk_wayline_pause).setOnClickListener(v -> pauseMission());
        rootView.findViewById(R.id.uxsdk_wayline_resume).setOnClickListener(v -> resumeMission());
        rootView.findViewById(R.id.uxsdk_wayline_stop).setOnClickListener(v -> stopMission());
        addPointButton.setOnClickListener(v -> setAddPointMode(!addPointMode));

        WPMZManager.getInstance().init(activity.getApplicationContext());
        WaypointMissionManager.getInstance().addWaypointMissionExecuteStateListener(missionStateListener);
        WaypointMissionManager.getInstance().addWaylineExecutingInfoListener(waylineExecutingInfoListener);
        WaypointMissionManager.getInstance().addWaypointActionListener(waypointActionListener);
        updateSelectedWaylineStatus();
    }

    void onMapReady() {
        if (addPointMode) {
            applyMapClickListener();
        }
    }

    void setExpanded(boolean expanded) {
        panel.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (!expanded) {
            setAddPointMode(false);
        }
    }

    void bringPanelToFront() {
        panel.bringToFront();
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
        WaypointMissionManager.getInstance().removeWaypointMissionExecuteStateListener(missionStateListener);
        WaypointMissionManager.getInstance().removeWaylineExecutingInfoListener(waylineExecutingInfoListener);
        WaypointMissionManager.getInstance().removeWaypointActionListener(waypointActionListener);
        clearMapOverlays(true);
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

        File outFile = new File(getMissionDir(), sanitizeFileName(displayName));
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

        currentMissionPath = outFile.getAbsolutePath();
        selectedWaylineIds.clear();
        setAddPointMode(false);
        markWaypointsFromKmz(currentMissionPath);
        statusText.setText("已导入：" + outFile.getName());
        updateSelectedWaylineStatus();
    }

    private void addWaypointFromMap(DJILatLng latLng) {
        if (latLng == null || !latLng.isAvailable()) {
            showToast("地图坐标无效");
            return;
        }
        editPoints.add(new DJILatLng(latLng.getLatitude(), latLng.getLongitude()));
        addMarker(latLng, "P" + editPoints.size());
        redrawEditRoute();
        currentMissionPath = "";
        selectedWaylineIds.clear();
        statusText.setText("已添加航点：" + editPoints.size() + "，高度 " + (int) DEFAULT_WAYPOINT_HEIGHT + "m，速度 " + (int) DEFAULT_WAYPOINT_SPEED + "m/s");
        updateSelectedWaylineStatus();
    }

    private void saveCurrentEditMission(boolean showSuccessToast) {
        if (editPoints.size() < 2) {
            showToast("至少添加 2 个航点后才能保存航线");
            return;
        }

        File outFile = new File(getMissionDir(), GENERATED_MISSION_PREFIX + System.currentTimeMillis() + ".kmz");
        WaylineMission waylineMission = createWaylineMission();
        WaylineMissionConfig missionConfig = createMissionConfig();
        Template template = createTemplate(editPoints);
        WPMZManager.getInstance().generateKMZFile(outFile.getAbsolutePath(), waylineMission, missionConfig, template);
        currentMissionPath = outFile.getAbsolutePath();
        selectedWaylineIds.clear();
        updateSelectedWaylineStatus();
        statusText.setText("已保存：" + outFile.getName());
        if (showSuccessToast) {
            showToast("航线 KMZ 已保存");
        }
    }

    private void uploadCurrentMission() {
        if (!editPoints.isEmpty()) {
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
        if (!hasMissionFile()) {
            showToast("请先上传 KMZ 文件");
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
        addPointMode = enabled;
        addPointButton.setSelected(enabled);
        addPointButton.setText(enabled ? "结束添加" : "添加航点");
        if (enabled) {
            applyMapClickListener();
            statusText.setText("点击地图添加航点");
        } else {
            DJIMap map = mapWidget.getMap();
            if (map != null) {
                map.removeOnMapClickListener(addPointListener);
            }
        }
    }

    private void applyMapClickListener() {
        DJIMap map = mapWidget.getMap();
        if (map != null) {
            map.removeOnMapClickListener(addPointListener);
            map.setOnMapClickListener(addPointListener);
        }
    }

    private void clearMission() {
        setAddPointMode(false);
        currentMissionPath = "";
        selectedWaylineIds.clear();
        clearMapOverlays(true);
        statusText.setText("已清空航线");
        executeInfoText.setText("执行信息：--");
        updateSelectedWaylineStatus();
    }

    private void markWaypointsFromKmz(String missionPath) {
        clearMapOverlays(true);
        try {
            List<Wayline> waylines = WPMZManager.getInstance()
                    .getKMZInfo(missionPath)
                    .getWaylineWaylinesParseInfo()
                    .getWaylines();
            if (waylines == null || waylines.isEmpty()) {
                showToast("KMZ 中没有航线");
                return;
            }
            for (int lineIndex = 0; lineIndex < waylines.size(); lineIndex++) {
                Wayline wayline = waylines.get(lineIndex);
                List<DJILatLng> linePoints = new ArrayList<>();
                List<WaylineExecuteWaypoint> waypoints = wayline.getWaypoints();
                if (waypoints == null) {
                    continue;
                }
                for (WaylineExecuteWaypoint waypoint : waypoints) {
                    if (waypoint.getLocation() == null) {
                        continue;
                    }
                    DJILatLng point = new DJILatLng(
                            waypoint.getLocation().getLatitude(),
                            waypoint.getLocation().getLongitude());
                    linePoints.add(point);
                    addMarker(point, "W" + lineIndex + "-" + waypoint.getWaypointIndex());
                }
                addRouteLine(linePoints, Color.GREEN);
            }
        } catch (Exception e) {
            showToast("解析 KMZ 失败：" + e.getMessage());
        }
    }

    private void addMarker(DJILatLng point, String title) {
        DJIMap map = mapWidget.getMap();
        if (map == null) {
            return;
        }
        DJIMarkerOptions markerOptions = new DJIMarkerOptions()
                .position(point)
                .title(title)
                .zIndex(20)
                .setInfoWindowEnable(true);
        DJIMarker marker = map.addMarker(markerOptions);
        if (marker != null) {
            pointMarkers.add(marker);
        }
    }

    private void redrawEditRoute() {
        clearRouteLines();
        addRouteLine(editPoints, Color.CYAN);
    }

    private void addRouteLine(List<DJILatLng> points, int color) {
        if (points.size() < 2 || mapWidget.getMap() == null) {
            return;
        }
        DJIPolylineOptions lineOptions = new DJIPolylineOptions();
        lineOptions.width(6f);
        lineOptions.color(color);
        lineOptions.addAll(points);
        DJIPolyline line = mapWidget.getMap().addPolyline(lineOptions);
        if (line != null) {
            routeLines.add(line);
        }
    }

    private void clearMapOverlays(boolean clearEditPoints) {
        for (DJIMarker marker : pointMarkers) {
            if (marker != null) {
                marker.remove();
            }
        }
        pointMarkers.clear();
        clearRouteLines();
        if (clearEditPoints) {
            editPoints.clear();
        }
    }

    private void clearRouteLines() {
        for (DJIPolyline line : routeLines) {
            if (line != null) {
                line.remove();
            }
        }
        routeLines.clear();
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

    private Template createTemplate(List<DJILatLng> points) {
        Template template = new Template();
        template.setWaypointInfo(createTemplateWaypointInfo(points));
        template.setCoordinateParam(createCoordinateParam());
        template.setUseGlobalTransitionalSpeed(true);
        template.setAutoFlightSpeed(DEFAULT_WAYPOINT_SPEED);
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

    private WaylineTemplateWaypointInfo createTemplateWaypointInfo(List<DJILatLng> points) {
        List<WaylineWaypoint> waypoints = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            DJILatLng point = points.get(i);
            WaylineWaypoint waypoint = new WaylineWaypoint();
            waypoint.setWaypointIndex(i);
            waypoint.setLocation(new WaylineLocationCoordinate2D(point.getLatitude(), point.getLongitude()));
            waypoint.setHeight(DEFAULT_WAYPOINT_HEIGHT);
            waypoint.setEllipsoidHeight(DEFAULT_WAYPOINT_HEIGHT);
            waypoint.setSpeed(DEFAULT_WAYPOINT_SPEED);
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
        DJILatLng firstPoint = points.get(0);
        yawParam.setPoiLocation(new WaylineLocationCoordinate3D(
                firstPoint.getLatitude(),
                firstPoint.getLongitude(),
                DEFAULT_WAYPOINT_HEIGHT));
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

    private void updateSelectedWaylineStatus() {
        selectedIdsText.setText(selectedWaylineIds.isEmpty()
                ? "航线ID：全部"
                : "航线ID：" + selectedWaylineIds);
    }

    private File getMissionDir() {
        File cacheRoot = activity.getExternalCacheDir();
        if (cacheRoot == null) {
            cacheRoot = activity.getCacheDir();
        }
        File baseDir = new File(cacheRoot, "wayline");
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        return baseDir;
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

    private String getErrorMessage(IDJIError error) {
        if (error == null) {
            return "";
        }
        if (!TextUtils.isEmpty(error.description())) {
            return error.description();
        }
        return error.errorCode();
    }

    private void showToast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    private void runOnUiThread(Runnable runnable) {
        activity.runOnUiThread(runnable);
    }
}
