package vn.futaland.app.features.zalo

import androidx.compose.runtime.Composable

// Temporary placeholders while the module is ported screen by screen.
@Composable fun ZaloConversationListView(navigator: ZaloNavigator) {}
@Composable fun ZaloCampaignsListView(navigator: ZaloNavigator) {}
@Composable fun ZaloLabelsScreen(navigator: ZaloNavigator) {}
@Composable fun ZaloSuggestionConfigScreen(navigator: ZaloNavigator) {}
@Composable fun ZaloChatDetailScreen(conversation: ZaloConversationModel, navigator: ZaloNavigator) {}
@Composable fun ZaloNewChatScreen(accounts: List<ZaloAccountModel>, navigator: ZaloNavigator) {}
@Composable fun ZaloCampaignDetailScreen(campaignId: String, navigator: ZaloNavigator) {}
@Composable fun ZaloCreateCampaignScreen(navigator: ZaloNavigator) {}
