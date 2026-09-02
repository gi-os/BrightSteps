## v1.1 — the count was low, and here is why

Three separate things were losing steps, and nothing in the app could show you any of them.

**Reboots ate whole days.** A reboot used to be spotted by the step counter going down. It only
goes down if you walked fewer steps since the reboot than you had before it — walk more and the
reboot is invisible, the jump looks like an ordinary one, and every step up to the old counter
value is quietly gone. A phone that restarted at noon on 8,000 steps and reached 9,000 by evening
recorded 1,000 for the day. Reboots are now detected from the clock that cannot hide one, and the
steps after a reboot are credited to the time since it rather than smeared back across hours the
phone spent switched off.

**Rounding threw away quiet hours.** Day totals were added up from hourly figures that had each
been rounded to a whole step first, so any hour holding less than half a step became nothing — and
a long stretch between readings is made entirely of hours like that. The day is now totalled
before it is rounded.

**Midnight moved.** Which day a walk counts toward is decided by the readings on either side of
midnight, and the every-15-minutes chain drifts. A reading is now pinned to midnight itself.

### New: the CHECK page

Tap CHECK on the home screen. It shows how many readings actually landed today against how many
should have, the longest gap, how many steps were discarded and why, and whether either of the two
things that silently stop the counting has happened — Android revoking activity recognition from
an app you have not opened in a while, or battery optimization deferring the background reading for
hours. Both can be fixed from that page.

It also runs a walk test. Start it, walk a counted hundred steps, and compare the two numbers: the
detector sees each step as it happens, while the counter is the sensor hub's own total and usually
waits to be sure you are walking before it commits. If the counter trails your real count, the loss
is in the hardware and no arithmetic recovers it. If both match and the daily total still reads
low, the fault is in the app — and the rest of the page says where.

### Also

- Activity recognition being revoked is now noticed by the background reader and flagged on the
  home screen, so the short days behind it are explained rather than mysterious.
- Intervals that get discarded are recorded with a reason instead of vanishing.
- Spring-forward and fall-back days now total exactly rather than within a few steps.
