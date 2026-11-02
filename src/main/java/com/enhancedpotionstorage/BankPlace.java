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

/** Where every dose slot goes as a unit: the main bank view, a numbered bank tab, or a bank tag. */
public enum BankPlace
{
	MAIN("Main bank view", 0),
	TAB_1("Bank tab 1", 1),
	TAB_2("Bank tab 2", 2),
	TAB_3("Bank tab 3", 3),
	TAB_4("Bank tab 4", 4),
	TAB_5("Bank tab 5", 5),
	TAB_6("Bank tab 6", 6),
	TAB_7("Bank tab 7", 7),
	TAB_8("Bank tab 8", 8),
	TAB_9("Bank tab 9", 9),
	TAG("A bank tag", -1);

	private final String name;
	/** The bank section: 0 for the untabbed part, the tab's number, or -1 for a tag. */
	final int section;

	BankPlace(String name, int section)
	{
		this.name = name;
		this.section = section;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
