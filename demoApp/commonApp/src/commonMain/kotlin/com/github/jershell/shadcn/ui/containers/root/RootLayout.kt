package com.github.jershell.shadcn.ui.containers.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.composeunstyled.theme.Theme
import com.github.jershell.features.root.ToolBar
import com.github.jershell.shadcn.components.button.Button
import com.github.jershell.shadcn.components.button.ButtonSize
import com.github.jershell.shadcn.components.button.ButtonText
import com.github.jershell.shadcn.components.button.ButtonVariant
import com.github.jershell.shadcn.components.drawer.Drawer
import com.github.jershell.shadcn.components.drawer.DrawerSide
import com.github.jershell.shadcn.components.drawer.DrawerTitle
import com.github.jershell.shadcn.components.sidebar.Sidebar
import com.github.jershell.shadcn.components.sidebar.SidebarCollapsible
import com.github.jershell.shadcn.components.sidebar.SidebarProvider
import com.github.jershell.shadcn.components.typography.H4
import com.github.jershell.shadcn.theme.ColorProps
import com.github.jershell.shadcn.theme.ColorTokens
import com.github.jershell.shadcn.theme.BaseTokens
import com.github.jershell.shadcn.ui.navigation.Component
import com.github.jershell.shadcn.ui.navigation.ComponentsRegistry
import com.github.jershell.shadcn.ui.navigation.Icons
import com.github.jershell.shadcn.ui.navigation.Overview
import com.github.jershell.shadcn.util.Log
import com.github.jershell.shadcn.demoapp.generated.resources.Res
import com.github.jershell.shadcn.demoapp.generated.resources.root_layout_components
import com.github.jershell.shadcn.demoapp.generated.resources.root_layout_icons
import com.github.jershell.shadcn.demoapp.generated.resources.root_layout_overview
import com.github.jershell.shadcn.demoapp.generated.resources.root_layout_shadcn_for_compose
import com.github.jershell.shadcn.demoapp.generated.resources.root_layout_text_in_footer
import org.jetbrains.compose.resources.stringResource

@Composable
fun RootLayout(navStack: NavBackStack<NavKey>, content: @Composable () -> Unit) {
    LaunchedEffect(navStack.toList()) {
        navStack.forEachIndexed { idx, item -> Log.debug("Navstack: [$idx] $item") }
    }
    ResponsiveDemoLayout(
        navigation = { modifier, collapsible, onNavigate -> DemoNavigation(navStack, modifier, collapsible, onNavigate) },
        toolbar = { label, expanded -> ToolBar(label, expanded) },
        content = content,
    )
}

/** The phone navigation overlays content; desktop collapse state is retained across resize. */
@Composable
internal fun ResponsiveDemoLayout(
    modifier: Modifier = Modifier,
    navigation: @Composable (modifier: Modifier, collapsible: SidebarCollapsible, onNavigate: () -> Unit) -> Unit,
    toolbar: @Composable (navigationLabel: String, expanded: Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    var desktopExpanded by rememberSaveable { mutableStateOf(true) }
    var mobileExpanded by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxSize().background(Theme[ColorProps][ColorTokens.sidebar]).safeDrawingPadding()) {
        val compact = maxWidth < BaseTokens.token768
        LaunchedEffect(compact) { mobileExpanded = false }
        val expanded = if (compact) mobileExpanded else desktopExpanded
        SidebarProvider(expanded = expanded, onExpandedChange = {
            if (compact) mobileExpanded = it else desktopExpanded = it
        }) {
            Row(Modifier.fillMaxSize()) {
                if (!compact) navigation(Modifier.fillMaxHeight(), SidebarCollapsible.Icon) {}
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    toolbar(if (expanded) "Close navigation" else "Open navigation", expanded)
                    Box(Modifier.weight(1f).fillMaxWidth().padding(BaseTokens.token16)) { content() }
                }
            }
            if (compact) Drawer(
                open = mobileExpanded,
                onOpenChange = { mobileExpanded = it },
                side = DrawerSide.Left,
                draggable = false,
                showDragHandle = false,
                panelWidth = minOf(BaseTokens.token320, maxWidth - BaseTokens.token48).coerceAtLeast(BaseTokens.token0),
                modifier = Modifier.safeDrawingPadding(),
            ) {
                Row(Modifier.fillMaxWidth().padding(BaseTokens.token8),
                    verticalAlignment = Alignment.CenterVertically) {
                    DrawerTitle("Browse components", Modifier.weight(1f))
                    Button({ mobileExpanded = false }, Modifier.semantics { contentDescription = "Close navigation" },
                        variant = ButtonVariant.Ghost, size = ButtonSize.Icon) { ButtonText("×") }
                }
                // The modal panel always shows the full labels regardless of desktop collapse state.
                SidebarProvider(expanded = true) {
                    navigation(Modifier.fillMaxWidth().weight(1f), SidebarCollapsible.None) { mobileExpanded = false }
                }
            }
        }
    }
}

@Composable
private fun DemoNavigation(navStack: NavBackStack<NavKey>, modifier: Modifier,
    collapsible: SidebarCollapsible, onNavigate: () -> Unit) {
    val s_root_layout_components = stringResource(Res.string.root_layout_components)
    val s_root_layout_icons = stringResource(Res.string.root_layout_icons)
    val s_root_layout_overview = stringResource(Res.string.root_layout_overview)
    val registryEntries = ComponentsRegistry.all()
    val currentScreen = navStack.lastOrNull()

    Sidebar(
        modifier = modifier,
        collapsible = collapsible,
        showRail = collapsible != SidebarCollapsible.None,
    ) {
        Header {
            if (isExpanded) {
                H4(stringResource(Res.string.root_layout_shadcn_for_compose))
            }
        }
        Content {
            Group {
                Menu {
                    Item {
                        Button(
                            label = s_root_layout_overview,
                            onClick = {
                                navStack.clear()
                                navStack.add(Overview)
                                onNavigate()
                            },
                            isSelected = currentScreen == Overview
                        )
                    }
                    Item {
                        Button(
                            label = s_root_layout_icons,
                            onClick = {
                                navStack.clear()
                                navStack.add(Icons)
                                onNavigate()
                            },
                            isSelected = currentScreen == Icons
                        )
                    }
                }
            }
            Separator()
            Group {
                Label(s_root_layout_components)
                Menu {
                    registryEntries.forEach {
                        Item(key = it.key.id) {
                            Button(
                                label = it.value.name,
                                isSelected = currentScreen is Component && it.key == currentScreen.id,
                                onClick = {
                                    navStack.clear()
                                    navStack.add(Component(it.key))
                                    onNavigate()
                                }
                            )
                        }
                    }
                }
            }
        }
        Footer {
            if (isExpanded) {
                H4(stringResource(Res.string.root_layout_text_in_footer))
            }
        }
    }

}

