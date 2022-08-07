# TAMAGAWA CONTEST DEFINED by ATS-4

java_import 'java.time.DayOfWeek'
java_import 'java.time.LocalDate'
java_import 'java.time.Year'
java_import 'java.time.ZoneId'
java_import 'java.time.temporal.TemporalAdjusters'
java_import 'qxsl.draft.Qxsl'
java_import 'qxsl.local.LocalCityBase'
java_import 'qxsl.ruler.Element'
java_import 'qxsl.ruler.Failure'
java_import 'qxsl.ruler.Program'
java_import 'qxsl.ruler.RuleKit'
java_import 'qxsl.ruler.Section'
java_import 'qxsl.ruler.Success'
java_import 'qxsl.utils.AssetUtil'

def schedule(year, month, nth, dayOfWeek)
	week = DayOfWeek.valueOf(dayOfWeek)
	date = LocalDate.of(year, month, 1)
	date.with(TemporalAdjusters.dayOfWeekInMonth(nth, week))
end

def opt_year(func_start_day, months = 9)
	year = Year.now.getValue
	date = func_start_day.call(year)
	span = date.until(LocalDate.now)
	(span.getMonths > months ? 1: 0) + year
end

# JAUTIL library
JAUTIL = RuleKit.load('jautil.lisp').pattern
ZONEID = ZoneId.of('Asia/Tokyo')

HOURDB = [13, 14]
BANDDB = [50_000]
CITYDB = LocalCityBase.load('rules/JI1YEG/tama.dat').toList

module ModeEnum
	MORSE = ['CW']
	PHONE = MORSE + ['SSB', 'AM', 'FM']
end

SCORES = {'CW' => 3}
SCORES.default = 2

def verify_time(time)
	HOURDB.include?(time.withZoneSameInstant(ZONEID).getHour)
end

def verify_code(code)
	CITYDB.any?{|c| c.code == code}
end

def verify_item(item, modeDB)
	time = item.value(Qxsl::TIME)
	call = item.value(Qxsl::CALL)
	band = item.value(Qxsl::BAND).intValue
	mode = item.value(Qxsl::MODE)
	code = item.getRcvd.value(Qxsl::CODE)
	return Failure.new(item, 'bad time') if not verify_time(time)
	return Failure.new(item, 'bad code') if not verify_code(code)
	return Failure.new(item, 'bad band') if not BANDDB.include?(band)
	return Failure.new(item, 'bad mode') if not modeDB.include?(mode)
	return Success.new(item, SCORES[mode])
end

def unique_item(item)
	Element.new(item.value(Qxsl::CALL))
end

def entity_item(item)
	Element.new(item.getRcvd.value(Qxsl::CODE))
end

class ProgramTama < Program
	def name()
		'多摩川コンテスト'
	end
	def host()
		'APOLLO'
	end
	def mail()
		'jk1mgc@example.com'
	end
	def link()
		'apollo.c.ooco.jp'
	end
	def help()
		AssetUtil.root.string('rules/JI1YEG/tama.md')
	end
	def get(name)
		eval name
	end
	def year()
		opt_year(method(:getStartDay))
	end
	def getStartDay(year)
		schedule(year, 11, 4, 'SUNDAY')
	end
	def getFinalDay(year)
		schedule(year, 11, 4, 'SUNDAY')
	end
	def getDeadLine(year)
		start_day(year).plusWeeks(2)
	end
	def limitMultipleEntry(code)
		1
	end
	def conflict(entries)
		entries.length > 1
	end
end

class SectionTama < Section
	def initialize(name, mode)
		super()
		@name = name
		@mode = mode
	end
	def name()
		@name
	end
	def code()
		'TAMA'
	end
	def getCityList()
		CITYDB
	end
	def verify(item)
		verify_item(JAUTIL.normalize(item, nil), @mode)
	end
	def unique(item)
		unique_item(item)
	end
	def entity(item)
		entity_item(item)
	end
	def result(list)
		score,mults = list.toArray
		score > 0? score * mults.size: 0
	end
end

INNER = '流域内'
OUTER = '流域外'
SWLER = 'SWL'
MORSE = '電信'
PHONE = '電信電話'

LIST = []
LIST.push SectionTama.new(INNER + MORSE, ModeEnum::MORSE)
LIST.push SectionTama.new(INNER + PHONE, ModeEnum::PHONE)
LIST.push SectionTama.new(OUTER + MORSE, ModeEnum::MORSE)
LIST.push SectionTama.new(OUTER + PHONE, ModeEnum::PHONE)
LIST.push SectionTama.new(SWLER,         ModeEnum::PHONE)

# returns contest definition
ProgramTama.new(*LIST)