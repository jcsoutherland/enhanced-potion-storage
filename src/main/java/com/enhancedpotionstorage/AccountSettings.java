/*
 * Copyright (c) 2026, jcsoutherland
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.enhancedpotionstorage;

import java.util.List;
import net.runelite.client.config.ConfigManager;

/**
 * The plugin's settings, kept per RuneScape account. RuneLite's settings panel shows and saves one value per
 * RuneLite profile; each change is copied to the logged-in account's own copy too, and logging in to an
 * account puts its copy back into the settings - or the defaults, for an account that has none.
 */
final class AccountSettings
{
	/** Every setting kept per account: all of them but the ones the plugin keeps itself. */
	static final List<String> KEYS = List.of("inBank", "showUnfinished", "inSearch", "hideStoreButton", "placeholders",
		"inTagTabs", "addToLayouts", "dragFromStore", "combineWithBank", "combinedWithdraw", "instantCounts",
		"warnDeposits", "showSidebar", "sidebarCount", EnhancedPotionStorageConfig.PLACE_KEY,
		EnhancedPotionStorageConfig.TAG_KEY);

	private static final String PREFIX = "account.";

	private final ConfigManager configManager;
	/** Settings being put back from an account's copy: not changes to copy back. */
	private boolean restoring;

	AccountSettings(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	static boolean isOwnKey(String key)
	{
		return key != null && key.startsWith(PREFIX);
	}

	/** Settings are being put back from an account's copy, not changed by the user. */
	boolean restoring()
	{
		return restoring;
	}

	/** A setting changed in the panel: the logged-in account's copy follows. */
	void changed(String key, String value)
	{
		if (restoring || !KEYS.contains(key) || configManager.getRSProfileKey() == null)
		{
			return;
		}
		if (value == null)
		{
			configManager.unsetRSProfileConfiguration(EnhancedPotionStorageConfig.GROUP, PREFIX + key);
		}
		else
		{
			configManager.setRSProfileConfiguration(EnhancedPotionStorageConfig.GROUP, PREFIX + key, value);
		}
	}

	/**
	 * An account logged in: its settings into the panel. An account with no copy yet takes the settings as they
	 * are - the ones it had before settings were kept per account - and they become its copy.
	 */
	void accountChanged()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}
		boolean any = KEYS.stream().anyMatch(k -> configManager.getRSProfileConfiguration(EnhancedPotionStorageConfig.GROUP, PREFIX + k) != null);
		restoring = true;
		try
		{
			for (String key : KEYS)
			{
				String mine = configManager.getRSProfileConfiguration(EnhancedPotionStorageConfig.GROUP, PREFIX + key);
				String now = configManager.getConfiguration(EnhancedPotionStorageConfig.GROUP, key);
				if (!any)
				{
					if (now != null)
					{
						configManager.setRSProfileConfiguration(EnhancedPotionStorageConfig.GROUP, PREFIX + key, now);
					}
				}
				else if (mine == null && now != null)
				{
					configManager.unsetConfiguration(EnhancedPotionStorageConfig.GROUP, key);
				}
				else if (mine != null && !mine.equals(now))
				{
					configManager.setConfiguration(EnhancedPotionStorageConfig.GROUP, key, mine);
				}
			}
		}
		finally
		{
			restoring = false;
		}
	}
}
