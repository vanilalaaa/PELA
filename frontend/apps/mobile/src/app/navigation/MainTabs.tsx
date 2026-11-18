import React from 'react';
import { createBottomTabNavigator, type BottomTabBarProps } from '@react-navigation/bottom-tabs';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { BottomNav, type BottomNavTab } from '../../core/ui/components';
import { HomeScreen, LeaderboardScreen, PracticeScreen, ProfileScreen, RoadmapScreen } from './screens/placeholders';
import type { MainTabParamList } from './types';

const Tab = createBottomTabNavigator<MainTabParamList>();

const ROUTE_TO_TAB: Record<keyof MainTabParamList, BottomNavTab> = {
  Home: 'home',
  Roadmap: 'lesson',
  Practice: 'practice',
  Leaderboard: 'leaderboard',
  Profile: 'profile',
};
const TAB_TO_ROUTE = Object.fromEntries(
  Object.entries(ROUTE_TO_TAB).map(([route, tab]) => [tab, route]),
) as Record<BottomNavTab, keyof MainTabParamList>;

/** Thanh tab dùng BottomNav của core/ui; chừa safe area dưới (thanh điều hướng Android). */
function MainTabBar({ state, navigation }: BottomTabBarProps) {
  const insets = useSafeAreaInsets();
  const current = state.routes[state.index]?.name as keyof MainTabParamList | undefined;
  return (
    <BottomNav
      active={ROUTE_TO_TAB[current ?? 'Home']}
      bottomInset={insets.bottom}
      onChange={(tab) => {
        const route = TAB_TO_ROUTE[tab];
        if (route !== current) navigation.navigate(route);
      }}
    />
  );
}

/** MainTabs: 5 tab theo figma-map (Trang chủ · Bài học · Luyện tập · Xếp hạng · Hồ sơ). */
export function MainTabs() {
  return (
    <Tab.Navigator tabBar={(props) => <MainTabBar {...props} />} screenOptions={{ headerShown: false }}>
      <Tab.Screen name="Home" component={HomeScreen} />
      <Tab.Screen name="Roadmap" component={RoadmapScreen} />
      <Tab.Screen name="Practice" component={PracticeScreen} />
      <Tab.Screen name="Leaderboard" component={LeaderboardScreen} />
      <Tab.Screen name="Profile" component={ProfileScreen} />
    </Tab.Navigator>
  );
}
