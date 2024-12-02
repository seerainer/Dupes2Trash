/*
 * Copyright 2024 Philipp Seerainer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.github.seerainer.dupes2trash;

import static org.eclipse.swt.events.MenuListener.menuShownAdapter;
import static org.eclipse.swt.events.SelectionListener.widgetSelectedAdapter;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

import org.apache.commons.io.IOUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

/**
 * Dupes2Trash
 *
 * Moves duplicate files to the trash.
 */
public class Dupes2Trash {
	public static void main(final String[] args) {
		System.setProperty("org.eclipse.swt.display.useSystemTheme", "true"); //$NON-NLS-1$ //$NON-NLS-2$

		final var display = new Display();
		final var shell = new Dupes2Trash(args).open(display);

		while (!shell.isDisposed())
			if (!display.readAndDispatch())
				display.sleep();
		display.dispose();
	}

	private static MenuItem menuItem(final Menu parent, final int state, final Menu menu, final SelectionListener listener,
			final int acc, final String text) {
		final var item = new MenuItem(parent, state);

		if (menu != null)
			item.setMenu(menu);
		if (listener != null)
			item.addSelectionListener(listener);
		if (acc > 0)
			item.setAccelerator(acc);
		if (text != null)
			item.setText(text);

		return item;
	}

	private List listA;
	private List listB;
	private Shell shell;
	private String dir;

	private Dupes2Trash(final String[] args) {
		if (args.length > 0)
			this.dir = args[0];
	}

	private void compare() {
		final var files = search();
		final var ht = new HashMap<Long, String>(files.size());
		listA.setRedraw(false);
		listB.setRedraw(false);
		listA.removeAll();
		listB.removeAll();

		files.forEach((final File f) -> {
			final var length = Long.valueOf(f.length());

			if (ht.containsKey(length)) {
				final var s1 = f.getAbsolutePath();
				final var s2 = ht.get(length);

				try (final var is1 = new FileInputStream(s1); final var is2 = new FileInputStream(s2)) {
					if (IOUtils.contentEquals(is1, is2)) {
						listA.add(s1);
						listB.add(s2);
					}
				} catch (final IOException e) {
					e.printStackTrace();
				}
			}

			if (f.isFile())
				ht.put(length, f.getAbsolutePath());
		});

		listA.setRedraw(true);
		listB.setRedraw(true);
	}

	private int message(final int style, final String message, final String text) {
		final var mb = new MessageBox(shell, style);
		mb.setMessage(message);
		mb.setText(text);
		return mb.open();
	}

	private void moveToTrash() {
		final var desktop = Desktop.getDesktop();
		var i = 0;

		while (listA.getItemCount() > 0) {
			final var f = new File(listB.getItem(0));

			if (f.exists() && f.canRead() && f.canWrite() && f.isFile() && desktop.moveToTrash(f))
				i++;

			listA.remove(0);
			listB.remove(0);
		}

		message(SWT.OK | SWT.ICON_INFORMATION, i + " file(s) moved to trash!", "Info"); //$NON-NLS-1$ //$NON-NLS-2$
		listA.setEnabled(false);
		listB.setEnabled(false);
	}

	private Shell open(final Display display) {
		final var darkMode = Display.isSystemDarkTheme();
		final var darkBack = new Color(0x30, 0x30, 0x30);
		final var darkFore = new Color(0xDD, 0xDD, 0xDD);

		if ("win32".equals(SWT.getPlatform()) && darkMode) { //$NON-NLS-1$
			display.setData("org.eclipse.swt.internal.win32.useDarkModeExplorerTheme", Boolean.TRUE); //$NON-NLS-1$
			display.setData("org.eclipse.swt.internal.win32.useShellTitleColoring", Boolean.TRUE); //$NON-NLS-1$
			display.setData("org.eclipse.swt.internal.win32.all.use_WS_BORDER", Boolean.TRUE); //$NON-NLS-1$
			display.setData("org.eclipse.swt.internal.win32.menuBarForegroundColor", darkFore); //$NON-NLS-1$
			display.setData("org.eclipse.swt.internal.win32.menuBarBackgroundColor", darkBack); //$NON-NLS-1$
		}

		shell = new Shell(display, SWT.SHELL_TRIM);
		shell.setMenuBar(new Menu(shell, SWT.BAR));
		shell.setLayout(new FillLayout());
		shell.setText("Dupes2Trash | Delete Duplicate Files"); //$NON-NLS-1$

		final var file = new Menu(shell, SWT.DROP_DOWN);
		menuItem(shell.getMenuBar(), SWT.CASCADE, file, null, 0, "&File"); //$NON-NLS-1$
		menuItem(file, SWT.PUSH, null, widgetSelectedAdapter(e -> openDir()), 0, "&Open Directory"); //$NON-NLS-1$
		final var delete = menuItem(file, SWT.PUSH, null, widgetSelectedAdapter(e -> moveToTrash()), 0, "&Dupes to Trash"); //$NON-NLS-1$
		menuItem(file, SWT.SEPARATOR, null, null, 0, null);
		menuItem(file, SWT.PUSH, null, widgetSelectedAdapter(e -> shell.close()), SWT.ESC, "E&xit\tEsc"); //$NON-NLS-1$
		file.addMenuListener(menuShownAdapter(e -> delete.setEnabled(listA.getItemCount() > 0)));

		final var form = new SashForm(shell, SWT.HORIZONTAL);
		form.setLayout(new FillLayout());
		form.setSashWidth(1);

		listA = new List(form, SWT.BORDER | SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL);
		listB = new List(form, SWT.BORDER | SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL);
		listA.setEnabled(false);
		listB.setEnabled(false);

		form.setWeights(50, 50);

		if (darkMode) {
			listA.setBackground(darkBack);
			listA.setForeground(darkFore);
			listB.setBackground(darkBack);
			listB.setForeground(darkFore);
		}

		shell.open();

		if (dir != null)
			openDir();

		return shell;
	}

	private void openDir() {
		if (dir == null)
			dir = new DirectoryDialog(shell).open();
		if (dir == null)
			return;

		final var f = new File(dir);

		if (f.exists() && f.isDirectory()) {
			final var wait = new Shell(shell, SWT.SYSTEM_MODAL | SWT.ON_TOP);
			wait.setSize(270, 80);

			final var r = shell.getDisplay().getBounds();
			final var s = wait.getBounds();
			final var x = (r.width - s.width) / 2;
			final var y = (r.height - s.height) / 2;
			wait.setLocation(x, y);

			final var label = new Label(wait, SWT.HORIZONTAL);
			label.setBounds(94, 36, 200, 50);
			label.setText("Please wait..."); //$NON-NLS-1$

			wait.open();
			compare();
			wait.close();

			if (listA.getItemCount() > 0) {
				listA.setEnabled(true);
				listB.setEnabled(true);

				final var mb = message(SWT.OK | SWT.CANCEL | SWT.ICON_WARNING,
						listA.getItemCount() + " duplicate file(s) found!\n\nMove to trash?", "Warning"); //$NON-NLS-1$ //$NON-NLS-2$

				if (mb == SWT.OK)
					moveToTrash();
			} else {
				listA.setEnabled(false);
				listB.setEnabled(false);
				message(SWT.OK | SWT.ICON_INFORMATION, "0 duplicate files found!", "Info"); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		dir = null;
	}

	private java.util.List<File> search() {
		final java.util.List<File> files = new ArrayList<>();
		final var dirs = new Stack<File>();
		final var startdir = new File(dir);

		if (startdir.isDirectory())
			dirs.push(startdir);

		while (dirs.size() > 0)
			for (final var file : dirs.pop().listFiles())
				if (file.isDirectory())
					dirs.push(file);
				else if (file.isFile())
					files.add(file);

		return files;
	}
}
