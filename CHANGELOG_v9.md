# Тактичний Урбаніст — v9 (правила старту)

## Виправлення
- Початкові ресурси тепер відповідають погодженим правилам: 1 енергія, 0 промисловості, 0 армії, 0 оборони.
- Початкове повідомлення журналу виправлено відповідно до стартового стану.
- SelfTest перевіряє всі чотири стартові значення в обох гравців.
- Оновлено тест раунду 2: промисловість дає дохід тільки після того, як її збудовано.


## Перевірки, виконані тут
- Java compile: PASS.
- Java SelfTest: PASS, 1 046 916 перевірок, 0 помилок.
- Map static test: PASS.
- Embedded UI syntax: PASS.
- HTTP integration: PASS, 6 сценаріїв + перепідключення, 949 HTTP-запитів, 0 помилок.
- Browser UI test: НЕ запускали — playwright-core відсутній.
- Android APK build: НЕ запускали — Android SDK/Gradle не доступні.

## v10 — sector-targeted attacks and capture
- Event attacks now target an opponent's sector rather than mutating only player-wide building counters.
- The event panel asks for an opponent and, for non-economic-crisis cards, a sector.
- Spy: damages sector defense first, otherwise industry (up to 2, respecting the existing energy-zero industry floor).
- General (`adm` internal card key): damages sector army first, otherwise industry, then damages one local defense if present.
- Disaster damages local industry; Sabotage damages local defense first, otherwise local industry.
- Economic Crisis remains a player-wide energy effect.
- When an attack clears a sector and the attacker has at least one army, the attacker may capture it; the defender must retain at least one other sector. Capture ownership is shown on the map.
- AI/Neighbor attacks without explicit UI sector target default to the first owned sector of their target.
- SelfTest now asserts that attacks don't damage global buildings when the selected sector is empty.
