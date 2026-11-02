package com.potionstorage;

import net.runelite.client.eventbus.EventBus;
import org.junit.Test;

/**
 * RuneLite's event bus refuses a {@code @Subscribe} method not named after its event, and a plugin it refuses
 * never starts - a failure that otherwise only shows up in a running client.
 */
public class SubscriptionsTest
{
	@Test
	public void theEventBusAcceptsEverySubscriber()
	{
		new EventBus().register(new PotionStoragePlugin());
	}
}
