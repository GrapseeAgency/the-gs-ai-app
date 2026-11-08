#!/usr/bin/env python3
"""Fail closed: named, executed platform tests, never an empty green suite."""
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

EXPECTED = {
    'ordinaryMarkdownTasksEditDeletePreserveDetailsAndDoNotMoveCompletion',
    'generatedReactTsxRunsActualTasksPersistsAndIsolatesResponses',
    'generatedTimerUsesActualSecondsPausesResetsAndNeverUsesPrices',
    'generatedBookingSavesOnlyActualLocalSelectionWithoutReservation',
    'generatedChecklistEditsDeletesAddsAndKeepsCompletionWithTheActualTask',
    'contextualActionsEditActualCodeShowDataAndLaunchPlatformSharing',
    'generatedPollUsesRealTotalsAndResetsForNewPayload',
    'generatedTerminalUsesRealFilesWorkerAndUnsupportedRefusal',
    'generatedCodeEditorAndWorkerPreserveUnicodeAndStopLoops',
    'generatedChartRangeAndPieSwitchUseOnlyActualWindowObservations',
    'generatedHtmlRunsRealButtonLogicAndSuppressesDynamicEmoji',
    'backgroundReplyAppearsInOsAndSettingsSuppressIt',
    'realCardDistinguishesTapFromLongPress',
    'hapticDispatchUsesPlatformConstantsAndHonorsToggle',
    'generatedCalculatorAcceptsExactNumbersAndNeverRendersEmoji',
    'generatedTableAllDisplaysEveryActualRow',
}


def check(root):
    seen = set()
    errors = []
    for path in Path(root).rglob('*.xml'):
        for case in ET.parse(path).getroot().iter('testcase'):
            if case.get('classname') != 'com.grapsee.gsai.PlatformRuntimeTest':
                continue
            name = case.get('name')
            if name in seen:
                errors.append(f'duplicate runtime result: {name}')
            seen.add(name)
            if any(case.find(tag) is not None for tag in ('skipped', 'failure', 'error')):
                details = []
                for tag in ('skipped', 'failure', 'error'):
                    node = case.find(tag)
                    if node is not None:
                        details.append((node.get('message', '') + '\n' + (node.text or '')).strip())
                errors.append(f'{name}: failed or skipped in {path}: ' + '\n'.join(details)[:6000])
    errors.extend(f'no execution result: {name}' for name in sorted(EXPECTED - seen))
    if errors:
        raise ValueError('; '.join(errors))
    return sorted(seen)


def self_test():
    import tempfile
    with tempfile.TemporaryDirectory() as root:
        p = Path(root) / 'results.xml'
        for mode in ('missing', 'skipped', 'failure', 'error', 'duplicate', 'passed'):
            suite = ET.Element('testsuite')
            for name in EXPECTED:
                if mode == 'missing':
                    continue
                case = ET.SubElement(suite, 'testcase', classname='com.grapsee.gsai.PlatformRuntimeTest', name=name)
                if mode in ('skipped', 'failure', 'error'):
                    ET.SubElement(case, mode)
                if mode == 'duplicate':
                    ET.SubElement(suite, 'testcase', classname='com.grapsee.gsai.PlatformRuntimeTest', name=name)
            ET.ElementTree(suite).write(p)
            try:
                check(root)
            except ValueError:
                assert mode != 'passed', mode
            else:
                assert mode == 'passed', mode
    print('Runtime evidence gate: 6 adversarial/positive cases passed')


if __name__ == '__main__':
    if sys.argv[1:] == ['--self-test']:
        self_test()
    else:
        try:
            for name in check(sys.argv[1]):
                print(f'Runtime executed: {name}')
        except (ValueError, ET.ParseError) as error:
            message = str(error).replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
            print(f'::error::{message}')
            sys.exit(1)
