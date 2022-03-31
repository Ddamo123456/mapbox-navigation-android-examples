package com.mapbox.examples.androidauto.car.preview

import android.text.SpannableString
import androidx.activity.OnBackPressedCallback
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.DurationSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.RoutePreviewNavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.mapbox.androidauto.ActiveGuidanceState
import com.mapbox.androidauto.MapboxCarApp
import com.mapbox.androidauto.car.navigation.speedlimit.CarSpeedLimitRenderer
import com.mapbox.androidauto.logAndroidAuto
import com.mapbox.androidauto.navigation.audioguidance.muteAudioGuidance
import com.mapbox.examples.androidauto.R
import com.mapbox.examples.androidauto.car.feedback.core.CarFeedbackSender
import com.mapbox.examples.androidauto.car.feedback.ui.CarFeedbackAction
import com.mapbox.examples.androidauto.car.feedback.ui.routePreviewCarFeedbackProvider
import com.mapbox.examples.androidauto.car.location.CarLocationRenderer
import com.mapbox.examples.androidauto.car.navigation.CarCameraMode
import com.mapbox.examples.androidauto.car.navigation.CarNavigationCamera
import com.mapbox.examples.androidauto.car.search.PlaceRecord
import com.mapbox.navigation.base.route.NavigationRoute

/**
 * After a destination has been selected. This view previews the route and lets
 * you select alternatives. From here, you can start turn-by-turn navigation.
 */
class CarRoutePreviewScreen(
    private val routePreviewCarContext: RoutePreviewCarContext,
    private val placeRecord: PlaceRecord,
    private val navigationRoutes: List<NavigationRoute>,
) : Screen(routePreviewCarContext.carContext) {

    private val routesProvider = PreviewRoutesProvider(navigationRoutes)
    var selectedIndex = 0
    val carRouteLine = CarRouteLine(routePreviewCarContext.mainCarContext, routesProvider)
    val carLocationRenderer = CarLocationRenderer(routePreviewCarContext.mainCarContext)
    val carSpeedLimitRenderer = CarSpeedLimitRenderer(routePreviewCarContext.mainCarContext)
    val carNavigationCamera = CarNavigationCamera(
        routePreviewCarContext.mapboxNavigation,
        CarCameraMode.OVERVIEW,
        CarCameraMode.FOLLOWING,
        routesProvider,
    )

    private val backPressCallback = object : OnBackPressedCallback(true) {

        override fun handleOnBackPressed() {
            logAndroidAuto("CarRoutePreviewScreen OnBackPressedCallback")
            routePreviewCarContext.mapboxNavigation.setNavigationRoutes(emptyList())
            screenManager.pop()
        }
    }

    init {
        logAndroidAuto("CarRoutePreviewScreen constructor")
        lifecycle.muteAudioGuidance()
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                logAndroidAuto("CarRoutePreviewScreen onResume")
                routePreviewCarContext.carContext.onBackPressedDispatcher.addCallback(backPressCallback)
                routePreviewCarContext.mapboxCarMap.registerObserver(carLocationRenderer)
                routePreviewCarContext.mapboxCarMap.registerObserver(carSpeedLimitRenderer)
                routePreviewCarContext.mapboxCarMap.registerObserver(carNavigationCamera)
                routePreviewCarContext.mapboxCarMap.registerObserver(carRouteLine)
            }

            override fun onPause(owner: LifecycleOwner) {
                logAndroidAuto("CarRoutePreviewScreen onPause")
                backPressCallback.remove()
                routePreviewCarContext.mapboxCarMap.unregisterObserver(carLocationRenderer)
                routePreviewCarContext.mapboxCarMap.unregisterObserver(carSpeedLimitRenderer)
                routePreviewCarContext.mapboxCarMap.unregisterObserver(carNavigationCamera)
                routePreviewCarContext.mapboxCarMap.unregisterObserver(carRouteLine)
            }
        })
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        navigationRoutes.forEach { navigationRoute ->
            val route = navigationRoute.directionsRoute
            val title = route.legs()?.first()?.summary() ?: placeRecord.name
            val duration = routePreviewCarContext.distanceFormatter.formatDistance(route.duration())
            val routeSpannableString = SpannableString("$duration $title")
            routeSpannableString.setSpan(
                DurationSpan.create(route.duration().toLong()),
                0, duration.length, 0
            )

            listBuilder.addItem(
                Row.Builder()
                    .setTitle(routeSpannableString)
                    .addText(duration)
                    .build()
            )
        }
        if (navigationRoutes.isNotEmpty()) {
            listBuilder.setSelectedIndex(selectedIndex)
            listBuilder.setOnSelectedListener { index ->
                val newRouteOrder = navigationRoutes.toMutableList()
                selectedIndex = index
                if (index > 0) {
                    val swap = newRouteOrder[0]
                    newRouteOrder[0] = newRouteOrder[index]
                    newRouteOrder[index] = swap
                    routesProvider.routes = newRouteOrder
                } else {
                    routesProvider.routes = navigationRoutes
                }
            }
        }

        return RoutePreviewNavigationTemplate.Builder()
            .setItemList(listBuilder.build())
            .setTitle(carContext.getString(R.string.car_action_preview_title))
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        CarFeedbackAction(
                            routePreviewCarContext.mapboxCarMap,
                            CarFeedbackSender(),
                            routePreviewCarFeedbackProvider(carContext)
                        ).getAction(this@CarRoutePreviewScreen)
                    )
                    .build()
            )
            .setHeaderAction(Action.BACK)
            .setNavigateAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.car_action_preview_navigate_button))
                    .setOnClickListener {
                        routePreviewCarContext.mapboxNavigation.setNavigationRoutes(routesProvider.routes)
                        MapboxCarApp.updateCarAppState(ActiveGuidanceState)
                    }
                    .build(),
            )
            .build()
    }
}
