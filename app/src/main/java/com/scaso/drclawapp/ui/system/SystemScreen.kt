package com.scaso.drclawapp.ui.system

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scaso.drclawapp.ui.device.DeviceScreen
import com.scaso.drclawapp.ui.experiments.ExperimentsContent
import com.scaso.drclawapp.ui.export.ExportScreen
import com.scaso.drclawapp.ui.infra.InfraScreenContent
import com.scaso.drclawapp.ui.metrics.MetricsScreen
import com.scaso.drclawapp.ui.plugins.PluginScreen
import com.scaso.drclawapp.ui.schedule.ScheduleScreen
import com.scaso.drclawapp.ui.vault.VaultScreen
import kotlinx.coroutines.launch

private val systemTabs = listOf("Infra", "Schedules", "Devices", "Metrics", "Vault", "Export", "Plugins", "Experiments")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemScreen() {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { systemTabs.size })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 0.dp,
            ) {
                systemTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> InfraScreenContent()
                    1 -> ScheduleScreen()
                    2 -> DeviceScreen()
                    3 -> MetricsScreen()
                    4 -> VaultScreen()
                    5 -> ExportScreen()
                    6 -> PluginScreen()
                    7 -> ExperimentsContent()
                }
            }
        }
    }
}
