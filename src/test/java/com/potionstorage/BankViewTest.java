package com.potionstorage;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/** A stored stack's left-click option reads like the bank's own, whatever its quantity setting. */
public class BankViewTest
{
	@Test
	public void defaultWithdrawFollowsTheBanksQuantitySetting()
	{
		assertEquals("1", BankView.defaultWithdraw(0, 0));
		assertEquals("5", BankView.defaultWithdraw(1, 0));
		assertEquals("10", BankView.defaultWithdraw(2, 0));
		assertEquals("14", BankView.defaultWithdraw(3, 14));
		assertEquals("1", BankView.defaultWithdraw(3, 0));
		assertEquals("All", BankView.defaultWithdraw(4, 0));
	}
}
