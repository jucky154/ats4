/*******************************************************************************
 * Amateur Radio Contest Administration System 'ATS-4' since 2017 April 2nd
 * License: GNU General Public License v3.0 (see LICENSE)
 * Author: Journal of Hamradio Informatics (https://pafelog.net)
*******************************************************************************/
package models

import java.util.UUID

import qxsl.draft.{Call, Qxsl}
import qxsl.model.Item
import qxsl.ruler._
import qxsl.sheet.SheetDecoder
import qxsl.table.TableManager

import ats4.data._
import ats4.root.ATS

import scala.jdk.CollectionConverters._

import play.api.data.{Form, Forms, OptionalMapping}
import play.api.libs.json.{Json, JsValue}

/**
 * 部門選択のフォームに入力されたデータです。
 *
 *
 * @param sect 部門
 * @param city 運用場所
 */
case class SectionFormData(
	sect: String,
	city: String
)


/**
 * 共通事項のフォームに入力されたデータです。
 *
 *
 * @param call 呼出符号
 * @param name 名前
 * @param post 郵便番号
 * @param addr 郵便宛先
 * @param mail メールアドレス
 * @param note 感想
 * @param uuid リアルタイムコンテストのトークン
 */
case class StationFormData(
	call: String,
	name: String,
	post: String,
	addr: String,
	mail: String,
	note: String,
	uuid: UUID
)


/**
 * 交信記録のファイルのフォームに入力されたデータです。
 *
 *
 * @param file このファイルの名前
 * @param keep このファイルを使う場合は真
 */
case class ArchiveFormData(
	file: String,
	keep: Boolean
)


/**
 * 交信記録の補助入力のフォームに入力されたデータです。
 *
 *
 * @param time 日時
 * @param call 呼出符号
 * @param band 周波数帯
 * @param mode 通信方式
 * @param sent 送信したナンバー
 * @param rcvd 受信したナンバー
 */
case class MarshalFormData(
	time: String,
	call: String,
	band: String,
	mode: String,
	sent: String,
	rcvd: String
)

/**
 * 交信の補助入力のデータと交信記録の変換を実装します。
 */
object MarshalFormData {
	/**
	 * 指定された補助入力のデータを交信記録に変換します。
	 *
	 * @param data 補助入力のデータ
	 * @return 交信記録
	 */
	def encode(data: MarshalFormData) = {
		val item = new Item()
		item.set(Qxsl.TIME, data.time)
		item.set(Qxsl.CALL, data.call)
		item.set(Qxsl.BAND, data.band)
		item.set(Qxsl.MODE, data.mode)
		item.getSent().set(Qxsl.CODE, data.sent)
		item.getRcvd().set(Qxsl.CODE, data.rcvd)
		item
	}

	/**
	 * 指定された交信記録を補助入力のデータに変換します。
	 *
	 * @param archive 交信記録
	 * @return 補助入力のデータ
	 */
	def decode(archive: ArchiveData) = util.Try {
		val seq = new TableManager().decode(archive.data)
		seq.asScala.map(item => MarshalFormData(
			time = item.getBoth().get(Qxsl.TIME).toString(),
			call = item.getBoth().get(Qxsl.CALL).toString(),
			band = item.getBoth().get(Qxsl.BAND).toString(),
			mode = item.getBoth().get(Qxsl.MODE).toString(),
			sent = item.getSent().get(Qxsl.CODE).toString(),
			rcvd = item.getRcvd().get(Qxsl.CODE).toString()
		))
	}.getOrElse(Seq())
}


/**
 * 書類提出のフォームに入力されたデータです。
 *
 *
 * @param station 共通事項のデータ
 * @param entries 部門選択のデータ
 * @param uploads 交信記録のデータ
 * @param marshal 交信記録の文字列
 */
case class ContestFormData(
	station: StationFormData,
	entries: Seq[SectionFormData],
	uploads: Seq[ArchiveFormData],
	marshal: Seq[MarshalFormData]
)


/**
 * 書類提出のフォームの初期値をデータベースから取得します。
 */
object ContestFormData {
	/**
	 * 指定された呼出符号の参加局の登録情報を返します。
	 *
	 * @param call 確認対象の呼出符号
	 * @param ats データベースの依存性注入
	 * @param rule コンテスト規約の依存性注入
	 * @return 初期値が入力されたフォームデータ
	 */
	def apply(call: String)(implicit ats: ATS, rule: Program) = {
		val station = ats.stations().byCall(call).get(0)
		val entries = ats.rankings().byCall(call).asScala.toList
		val uploads = ats.archives().byCall(call).asScala.toList
		val archive = uploads.filter(_.file.nonEmpty)
		val marshal = uploads.filter(_.file.isEmpty)
		new ContestFormData(
			StationFormData(
				call = station.call,
				name = station.name,
				post = station.post,
				addr = station.addr,
				mail = station.mail,
				note = station.note,
				uuid = UUID.fromString(station.uuid)
			),
			entries.map(r => new SectionFormData(r.sect, r.city)),
			archive.map(a => new ArchiveFormData(a.file, true)),
			marshal.map(MarshalFormData.decode).flatten
		)
	}
}


/**
 * 部門選択のフォームとデータの関連付けと検証を実装します。
 *
 *
 * @param ats データベースの依存性注入
 * @param rule コンテスト規約の依存性注入
 */
class SectionForm(implicit ats: ATS, rule: Program) extends Form[SectionFormData](
	Forms.mapping(
		"sect" -> Forms.text,
		"city" -> Forms.text
	)
	(SectionFormData.apply)
	(SectionFormData.unapply).verifying(s => {
		rule.section(s.sect).isAbsence() ||
		rule.section(s.sect).getCityList().asScala.exists(_.name() == s.city)
	}), Map.empty, Nil, None
)


/**
 * 共通事項のフォームとデータの関連付けと検証を実装します。
 *
 *
 * @param ats データベースの依存性注入
 * @param rule コンテスト規約の依存性注入
 */
class StationForm(implicit ats: ATS, rule: Program) extends Form[StationFormData](
	Forms.mapping(
		"call" -> CallSign.mapping,
		"name" -> Forms.nonEmptyText,
		"post" -> Forms.nonEmptyText,
		"addr" -> Forms.nonEmptyText,
		"mail" -> Forms.email,
		"note" -> Forms.text,
		"uuid" -> new Token().mapping
	)
	(StationFormData.apply)
	(StationFormData.unapply), Map.empty, Nil, None
)


/**
 * 呼出符号のフォームの検証を実装します。
 */
object CallSign {
	/**
	 * 指定された呼出符号を検証します。
	 *
	 * @param call 呼出符号
	 * @return 受理可能な場合は真
	 */
	def valid(call: String) = new Call(call).valid()

	/**
	 * 指定された呼出符号を正規化します。
	 *
	 * @param call 呼出符号
	 * @return 正規化された呼出符号
	 */
	def apply(call: String) = new Call(call).value()

	/**
	 * 呼出符号のマッピングを構築します。
	 *
	 * @return マッピング
	 */
	def mapping = Forms.nonEmptyText.verifying(valid(_)).transform(apply(_), identity[String])
}


/**
 * トークンのフォームの検証を実装します。
 *
 *
 * @param ats データベースの依存性注入
 */
class Token(implicit ats: ATS) {
	/**
	 * 重複を排除してトークンを発行します。
	 *
	 * @return トークン
	 */
	def newUUID = ats.stations().createUUID()

	/**
	 * 呼出符号のマッピングを構築します。
	 *
	 * @return マッピング
	 */
	def mapping = OptionalMapping(Forms.uuid).transform(_.getOrElse(newUUID), Some[UUID](_))
}


/**
 * 交信記録のファイルのフォームとデータの関連付けと検証を実装します。
 *
 *
 * @param ats データベースの依存性注入
 * @param rule コンテスト規約の依存性注入
 */
class ArchiveForm(implicit ats: ATS, rule: Program) extends Form[ArchiveFormData](
	Forms.mapping(
		"file" -> Forms.nonEmptyText,
		"keep" -> Forms.boolean,
	)
	(ArchiveFormData.apply)
	(ArchiveFormData.unapply), Map.empty, Nil, None
)


/**
 * 交信記録の補助入力のフォームとデータの関連付けと検証を実装します。
 */
class MarshalForm extends Form[MarshalFormData](
	Forms.mapping(
		"time" -> Forms.nonEmptyText,
		"call" -> Forms.nonEmptyText,
		"band" -> Forms.nonEmptyText,
		"mode" -> Forms.nonEmptyText,
		"sent" -> Forms.nonEmptyText,
		"rcvd" -> Forms.nonEmptyText
	)
	(MarshalFormData.apply)
	(MarshalFormData.unapply), Map.empty, Nil, None
)


/**
 * 書類提出のフォームとデータの関連付けと検証を実装します。
 *
 *
 * @param ats データベースの依存性注入
 * @param rule コンテスト規約の依存性注入
 */
class ContestForm(implicit ats: ATS, rule: Program) extends Form[ContestFormData](
	Forms.mapping(
		"station" -> new StationForm().mapping,
		"entries" -> Forms.seq(new SectionForm().mapping).verifying(new Conflict().ok(_)),
		"uploads" -> Forms.seq(new ArchiveForm().mapping),
		"marshal" -> Forms.seq(new MarshalForm().mapping)
	)
	(ContestFormData.apply)
	(ContestFormData.unapply), Map.empty, Nil, None
)


/**
 * 部門選択のフォームの整合性を検証します。
 *
 *
 * @param rule コンテスト規約の依存性注入
 */
class Conflict(implicit rule: Program) {
	/**
	 * 指定された部門選択の整合性を検証します。
	 *
	 * @param sectionList 部門選択のリスト
	 * @return 受理可能な場合は真
	 */
	def ok(sectionList: Seq[SectionFormData]): Boolean = {
		val rules = sectionList.map(_.sect).map(rule.section)
		! rule.conflict(rules.filterNot(_.isAbsence).toArray)
	}
}


/**
 * 全ての部門における全ての参加局の得点状況を格納したJSONデータを生成します。
 *
 *
 * @param ats データベースの依存性注入
 * @param rule コンテスト規約の依存性注入
 */
class RankingTableToJson(implicit ats: ATS, rule: Program) {
	/**
	 * JSONの文字列を生成します。
	 *
	 * @return JSONの文字列
	 */
	def json = Json.stringify(toJS(rule))

	/**
	 * 指定された規約に対し、得点状況を格納したJSONの文字列を生成します。
	 *
	 * @param rule 対象の規約
	 * @return JSONの文字列
	 */
	def toJS(rule: Program): JsValue = {
		Json.toJson(rule.asScala.toSeq.map(s => s.name() -> toJS(s)).toMap)
	}

	/**
	 * 指定された部門に対し、得点状況を格納したJSONの文字列を生成します。
	 *
	 * @param rule 対象の部門
	 * @return JSONの文字列
	 */
	def toJS(rule: Section): JsValue = {
		Json.toJson(ats.rankings().bySect(rule).asScala.map(toJS))
	}

	/**
	 * 指定された参加局に対し、得点状況を格納したJSONの文字列を生成します。
	 *
	 * @param data 対象の参加局
	 * @return JSONの文字列
	 */
	def toJS(data: RankingData): JsValue = Json.toJson(Map(
		"call" -> Json.toJson(data.call),
		"score" -> Json.toJson(data.score),
		"total" -> Json.toJson(data.total)
	))
}


/**
 * JARLサマリーシートの内容を抽出して、JSONデータに変換します。
 *
 *
 * @param decoder 対象のサマリーシート
 */
class SheetDecoderToJson(decoder: SheetDecoder) {
	/**
	 * サマリーシートの内容をJSONの文字列に変換します。
	 *
	 * @return JSONの文字列
	 */
	def json = Json.stringify(Json.toJson(Map(
		"call" -> decoder.getString("CALLSIGN"),
		"name" -> decoder.getString("NAME"),
		"addr" -> decoder.getString("ADDRESS"),
		"mail" -> decoder.getString("EMAIL"),
		"note" -> decoder.getString("COMMENTS")
	)))
}
