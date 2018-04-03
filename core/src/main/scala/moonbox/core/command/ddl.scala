package moonbox.core.command

import moonbox.common.util.Utils
import moonbox.core.catalog._
import moonbox.core.{MbFunctionIdentifier, MbSession, MbTableIdentifier}
import org.apache.spark.sql.Row
import org.apache.spark.sql.types.{StructField, StructType}

sealed trait DDL

case class CreateOrganization(
	name: String,
	comment: Option[String],
	ignoreIfExists: Boolean) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val organization = CatalogOrganization(
			name = name,
			description = comment,
			createBy = ctx.userId,
			updateBy = ctx.userId
		)
		mbSession.catalog.createOrganization(organization, ignoreIfExists)
		Seq.empty[Row]
	}
}

case class AlterOrganizationSetName(
	name: String,
	newName: String) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		mbSession.catalog.renameOrganization(name, newName, ctx.userId)
		Seq.empty[Row]
	}
}

case class AlterOrganizationSetComment(
	name: String,
	comment: String) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val existOrganization: CatalogOrganization = mbSession.catalog.getOrganization(name)
		mbSession.catalog.alterOrganization(
			existOrganization.copy(description = Some(comment), updateBy = ctx.userId, updateTime = Utils.now)
		)
		Seq.empty[Row]
	}
}

case class DropOrganization(
	name: String,
	ignoreIfNotExists: Boolean,
	cascade: Boolean) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		mbSession.catalog.dropOrganization(name, ignoreIfNotExists, cascade)
		Seq.empty[Row]
	}
}

case class CreateSa(
	name: String,
	password: String,
	organization: String,
	ignoreIfExists: Boolean) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val catalogOrganization = mbSession.catalog.getOrganization(organization)
		val catalogUser = CatalogUser(
			name = name,
			password = password,
			account = true,
			ddl = true,
			grantAccount = true,
			grantDdl = true,
			grantDmlOn = true,
			isSA = true,
			organizationId = catalogOrganization.id.get,
			createBy = ctx.userId,
			updateBy = ctx.userId
		)
		mbSession.catalog.createUser(catalogUser, catalogOrganization.name, ignoreIfExists)
		Seq.empty[Row]
	}
}

case class AlterSaSetName(
	name: String,
	newName: String,
	organization: String) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val catalogOrganization: CatalogOrganization = mbSession.catalog.getOrganization(organization)
		val existUser: CatalogUser = mbSession.catalog.getUser(catalogOrganization.id.get, name)
		require(existUser.isSA, s"ROOT can not alter non-sa.")
		mbSession.catalog.renameUser(catalogOrganization.id.get, organization, name, newName, ctx.userId)

		Seq.empty[Row]
	}
}

case class AlterSaSetPassword(
	name: String,
	newPassword: String,
	organization: String) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val catalogOrganization: CatalogOrganization = mbSession.catalog.getOrganization(organization)
		val existUser: CatalogUser = mbSession.catalog.getUser(catalogOrganization.id.get, name)
		require(existUser.isSA, s"ROOT can not alter non-sa.")
		mbSession.catalog.alterUser(
			existUser.copy(password = newPassword, updateBy = ctx.userId, updateTime = Utils.now)
		)
		Seq.empty[Row]
	}
}

case class DropSa(
	name: String,
	organization: String,
	ignoreIfNotExists: Boolean
	) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val catalogOrganization: CatalogOrganization = mbSession.catalog.getOrganization(organization)
		val existUser: CatalogUser = mbSession.catalog.getUser(catalogOrganization.id.get, name)
		require(existUser.isSA, s"ROOT can drop non-sa.")
		mbSession.catalog.dropUser(catalogOrganization.id.get, organization, name, ignoreIfNotExists)
		Seq.empty[Row]
	}
}

case class CreateUser(
	name: String,
	password: String,
	ignoreIfExists: Boolean) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val catalogUser = CatalogUser(
			name = name,
			password = password,
			organizationId = ctx.organizationId,
			createBy = ctx.userId,
			updateBy = ctx.userId
		)
		mbSession.catalog.createUser(catalogUser, ctx.organizationName, ignoreIfExists)
		Seq.empty[Row]
	}
}

case class AlterUserSetName(
	name: String,
	newName: String) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		mbSession.catalog.renameUser(ctx.organizationId, ctx.organizationName, name, newName, ctx.userId)
		Seq.empty[Row]
	}
}

case class AlterUserSetPassword(
	name: String,
	newPassword: String) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val existUser: CatalogUser = mbSession.catalog.getUser(ctx.organizationId, name)
		mbSession.catalog.alterUser(
			existUser.copy(
				password = newPassword,
				updateBy = ctx.userId,
				updateTime = Utils.now
			)
		)
		Seq.empty[Row]
	}
}

case class DropUser(
	name: String,
	ignoreIfNotExists: Boolean) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		mbSession.catalog.dropUser(ctx.organizationId, ctx.organizationName, name, ignoreIfNotExists)
		Seq.empty[Row]
	}
}

case class CreateGroup(
	name: String,
	comment: Option[String],
	ignoreIfExists: Boolean) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val catalogGroup = CatalogGroup(
			name = name,
			description = comment,
			organizationId = ctx.organizationId,
			createBy = ctx.userId,
			updateBy = ctx.userId
		)
		mbSession.catalog.createGroup(catalogGroup, ctx.organizationName, ignoreIfExists)
		Seq.empty[Row]
	}
}

case class AlterGroupSetName(
	name: String,
	newName: String) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		mbSession.catalog.renameGroup(ctx.organizationId, ctx.organizationName, name, newName, ctx.userId)
		Seq.empty[Row]
	}
}

case class AlterGroupSetComment(
	name: String,
	comment: String) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val existGroup: CatalogGroup = mbSession.catalog.getGroup(ctx.organizationId, name)
		mbSession.catalog.alterGroup(
			existGroup.copy(
				description = Some(comment),
				updateBy = ctx.userId,
				updateTime = Utils.now
			)
		)
		Seq.empty[Row]
	}
}

case class AlterGroupSetUser(
	name: String,
	addUsers: Seq[String] = Seq(),
	removeUsers: Seq[String] = Seq(),
	addFirst: Boolean = true) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val groupId: Long = mbSession.catalog.getGroup(ctx.organizationId, name).id.get

		def addUsersToGroup() = {
			val users: Seq[CatalogUser] = mbSession.catalog.getUsers(ctx.organizationId, addUsers)
			require(addUsers.size == users.size, s"User does not exist: '${addUsers.diff(users.map(_.name)).mkString(", ")}' ")
			val catalogUserGroupRel = CatalogUserGroupRel(
				groupId = groupId,
				users = users.map(_.id.get),
				createBy = ctx.userId,
				updateBy = ctx.userId
			)
			mbSession.catalog.createUserGroupRel(catalogUserGroupRel, ctx.organizationName, name, addUsers)
		}

		def removeUsersFromGroup() = {
			val users: Seq[CatalogUser] = mbSession.catalog.getUsers(ctx.organizationId, removeUsers)
			require(removeUsers.size == users.size, s"User does not exist: '${removeUsers.diff(users.map(_.name)).mkString(", ")}' ")
			val catalogUserGroupRel = CatalogUserGroupRel(
				groupId = groupId,
				users = users.map(_.id.get),
				createBy = ctx.userId,
				updateBy = ctx.userId
			)
			mbSession.catalog.dropUserGroupRel(catalogUserGroupRel, ctx.organizationName, name, removeUsers)
		}

		if (addFirst) {
			if (addUsers.nonEmpty) {
				addUsersToGroup()
			}
			if (removeUsers.nonEmpty) {
				removeUsersFromGroup()
			}
		} else {
			if (removeUsers.nonEmpty) {
				removeUsersFromGroup()
			}
			if (addUsers.nonEmpty) {
				addUsersToGroup()
			}
		}
		Seq.empty[Row]
	}
}

case class DropGroup(
	name: String,
	ignoreIfNotExists: Boolean,
	cascade: Boolean) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		mbSession.catalog.dropGroup(ctx.organizationId, ctx.organizationName, name, ignoreIfNotExists, cascade)
		Seq.empty[Row]
	}
}

case class MountDatasource(
	name: String,
	props: Map[String, String],
	ignoreIfExists: Boolean) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val catalogDatasource = CatalogDatasource(
			name = name,
			properties = props,
			description = None,
			organizationId = ctx.organizationId,
			createBy = ctx.userId,
			updateBy = ctx.userId
		)
		mbSession.catalog.createDatasource(catalogDatasource, ctx.organizationName, ignoreIfExists)
		Seq.empty[Row]
	}
}

case class AlterDatasourceSetName(
	name: String,
	newName: String) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		mbSession.catalog.renameDatasource(ctx.organizationId, ctx.organizationName, name, newName, ctx.userId)
		Seq.empty[Row]
	}
}

case class AlterDatasourceSetOptions(
	name: String,
	props: Map[String, String]) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val existDatasource: CatalogDatasource = mbSession.catalog.getDatasource(ctx.organizationId, name)
		mbSession.catalog.alterDatasource(
			existDatasource.copy(
				properties = existDatasource.properties ++ props,
				updateBy = ctx.userId,
				updateTime = Utils.now
			)
		)
		Seq.empty[Row]
	}
}

case class UnmountDatasource(
	name: String,
	ignoreIfNotExists: Boolean) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		mbSession.catalog.dropDatasource(ctx.organizationId, ctx.organizationName, name, ignoreIfNotExists)
		Seq.empty[Row]
	}
}

case class CreateDatabase(
	name: String,
	comment: Option[String],
	ignoreIfExists: Boolean) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val catalogDatabase = CatalogDatabase(
			name = name,
			description = comment,
			organizationId = ctx.organizationId,
			createBy = ctx.userId,
			updateBy = ctx.userId
		)
		mbSession.catalog.createDatabase(catalogDatabase, ctx.organizationName, ignoreIfExists)
		Seq.empty[Row]
	}
}

case class AlterDatabaseSetName(
	name: String,
	newName: String) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		mbSession.catalog.renameDatabase(ctx.organizationId, ctx.organizationName, name, newName, ctx.userId)
		Seq.empty[Row]
	}
}

case class AlterDatabaseSetComment(
	name: String,
	comment: String) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val existDatabase: CatalogDatabase = mbSession.catalog.getDatabase(ctx.organizationId, name)
		mbSession.catalog.alterDatabase(
			existDatabase.copy(
				description = Some(comment),
				updateBy = ctx.userId,
				updateTime = Utils.now
			)
		)
		Seq.empty[Row]
	}
}

case class DropDatabase(
	name: String,
	ignoreIfNotExists: Boolean,
	cascade: Boolean) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		mbSession.catalog.dropDatabase(ctx.organizationId, ctx.organizationName, name, ignoreIfNotExists, cascade)
		Seq.empty[Row]
	}
}

case class MountTable(
	table: MbTableIdentifier,
	schema: Option[StructType],
	props: Map[String, String],
	isStream: Boolean,
	ignoreIfExists: Boolean) extends MbRunnableCommand with DDL {
	// TODO schema

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val (databaseId, database)= table.database match {
			case Some(db) =>
				val currentDatabase: CatalogDatabase = mbSession.catalog.getDatabase(ctx.organizationId, db)
				(currentDatabase.id.get, currentDatabase.name)
			case None =>
				(ctx.databaseId, ctx.databaseName)
		}
		val catalogTable = CatalogTable(
			name = table.table,
			description = None,
			databaseId = databaseId,
			properties = props,
			isStream = isStream,
			createBy = ctx.userId,
			updateBy = ctx.userId
		)
		mbSession.catalog.createTable(catalogTable, ctx.organizationName, database, ignoreIfExists)
		Seq.empty[Row]
	}
}

case class MountTableWithDatasoruce(
	datasource: String,
	tables: Seq[(MbTableIdentifier, Option[Seq[StructField]], Map[String, String])],
	isStream: Boolean,
	ignoreIfExists: Boolean) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val catalogDatasource = mbSession.catalog.getDatasource(ctx.organizationId, datasource)
		// TODO columns
		tables.foreach { case (table, columns, props) =>
			val (databaseId, database)= table.database match {
				case Some(db) =>
					val currentDatabase: CatalogDatabase = mbSession.catalog.getDatabase(ctx.organizationId, db)
					(currentDatabase.id.get, currentDatabase.name)
				case None =>
					(ctx.databaseId, ctx.databaseName)
			}
			val catalogTable = CatalogTable(
				name = table.table,
				description = None,
				databaseId = databaseId,
				properties = catalogDatasource.properties ++ props,
				isStream = isStream,
				createBy = ctx.userId,
				updateBy = ctx.userId
			)
			mbSession.catalog.createTable(catalogTable, ctx.organizationName, database, ignoreIfExists)
		}
		Seq.empty[Row]
	}
}

case class AlterTableSetName(
	table: MbTableIdentifier,
	newTable: MbTableIdentifier) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		require(table.database == newTable.database, s"Rename table cant not rename database")
		val (databaseId, database) = table.database match {
			case Some(db) =>
				val currentDatabase: CatalogDatabase = mbSession.catalog.getDatabase(ctx.organizationId, db)
				(currentDatabase.id.get, currentDatabase.name)
			case None =>
				(ctx.databaseId, ctx.databaseName)
		}
		mbSession.catalog.renameTable(databaseId, ctx.organizationName, database, table.table, newTable.table, ctx.userId)
		Seq.empty[Row]
	}
}

case class AlterTableSetOptions(
	table: MbTableIdentifier,
	props: Map[String, String]) extends MbRunnableCommand with DDL {
	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val databaseId = table.database match {
			case Some(db) =>
				val currentDatabase: CatalogDatabase = mbSession.catalog.getDatabase(ctx.organizationId, db)
				currentDatabase.id.get
			case None =>
				ctx.databaseId
		}
		val existTable: CatalogTable = mbSession.catalog.getTable(databaseId, table.table)
		mbSession.catalog.alterTable(
			existTable.copy(
				properties = existTable.properties ++ props,
				updateBy = ctx.userId,
				updateTime = Utils.now
			)
		)
		Seq.empty[Row]
	}
}

case class AlterTableAddColumns(
	table: MbTableIdentifier,
	columns: StructType) extends MbRunnableCommand with DDL {
	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		Seq.empty[Row]
	}
}

case class AlterTableChangeColumn(
	table: MbTableIdentifier,
	column: StructField) extends MbRunnableCommand with DDL {
	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		Seq.empty[Row]
	}
}

case class AlterTableDropColumn(
	table: MbTableIdentifier,
	column: String) extends MbRunnableCommand with DDL {
	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		Seq.empty[Row]
	}
}

case class UnmountTable(
	table: MbTableIdentifier,
	ignoreIfNotExists: Boolean) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val (databaseId, database) = table.database match {
			case Some(db) =>
				val currentDatabase: CatalogDatabase = mbSession.catalog.getDatabase(ctx.organizationId, db)
				(currentDatabase.id.get, currentDatabase.name)
			case None =>
				(ctx.databaseId, ctx.databaseName)
		}
		mbSession.catalog.dropTable(databaseId, ctx.organizationName, database, table.table, ignoreIfNotExists)
		Seq.empty[Row]
	}
}


case class CreateFunction(
	function: MbFunctionIdentifier,
	props: Map[String, String],
	ignoreIfExists: Boolean) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {

		val (databaseId, database) = function.database match {
			case Some(db) =>
				val currentDatabase: CatalogDatabase = mbSession.catalog.getDatabase(ctx.organizationId, db)
				(currentDatabase.id.get, currentDatabase.name)
			case None =>
				(ctx.databaseId, ctx.databaseName)
		}
		mbSession.catalog.createFunction(
			CatalogFunction(
				name = function.func,
				databaseId = databaseId,
				description = None,
				className = "",
				resources = Seq(),
				createBy = ctx.userId,
				updateBy = ctx.userId
			), ctx.organizationName, database, ignoreIfExists
		)
		Seq.empty[Row]
	}
}

case class AlterFunctionSetName(
	function: MbFunctionIdentifier,
	newFunction: MbFunctionIdentifier) extends MbRunnableCommand with DDL {
	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		require(function.database == function.database, s"Rename function cant not rename database")
		val (databaseId, database) = function.database match {
			case Some(db) =>
				val currentDatabase: CatalogDatabase = mbSession.catalog.getDatabase(ctx.organizationId, db)
				(currentDatabase.id.get, currentDatabase.name)
			case None =>
				(ctx.databaseId, ctx.databaseName)
		}
		mbSession.catalog.renameFunction(databaseId,
			ctx.organizationName, database, function.func, newFunction.func, ctx.userId)
		Seq.empty[Row]
	}
}

case class AlterFunctionSetOptions(
	function: MbFunctionIdentifier,
	props: Map[String, String]) extends MbRunnableCommand with DDL {
	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		// TODO
		val databaseId = function.database match {
			case Some(db) =>
				val currentDatabase: CatalogDatabase = mbSession.catalog.getDatabase(ctx.organizationId, db)
				currentDatabase.id.get
			case None =>
				ctx.databaseId
		}
		val existFunction = mbSession.catalog.getFunction(databaseId, function.func)
		mbSession.catalog.alterFunction(
			existFunction.copy(
				updateBy = ctx.userId,
				updateTime = Utils.now
			)
		)
		Seq.empty[Row]
	}
}

case class DropFunction(
	function: MbFunctionIdentifier,
	ignoreIfNotExists: Boolean) extends MbRunnableCommand with DDL {
	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val (databaseId, database) = function.database match {
			case Some(db) =>
				val currentDatabase: CatalogDatabase = mbSession.catalog.getDatabase(ctx.organizationId, db)
				(currentDatabase.id.get, currentDatabase.name)
			case None =>
				(ctx.databaseId, ctx.databaseName)
		}
		mbSession.catalog.dropFunction(databaseId, ctx.organizationName, database, function.func, ignoreIfNotExists)
		Seq.empty[Row]
	}
}

case class CreateView(
	view: MbTableIdentifier,
	query: String,
	comment: Option[String],
	ignoreIfExists: Boolean) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val (databaseId, database)= view.database match {
			case Some(db) =>
				val currentDatabase: CatalogDatabase = mbSession.catalog.getDatabase(ctx.organizationId, db)
				(currentDatabase.id.get, currentDatabase.name)
			case None =>
				(ctx.databaseId, ctx.databaseName)
		}
		val catalogView = CatalogView(
			name = view.table,
			databaseId = databaseId,
			description = comment,
			cmd = query,
			createBy = ctx.userId,
			updateBy = ctx.userId
		)
		mbSession.catalog.createView(catalogView, ctx.organizationName, database, ignoreIfExists)
		Seq.empty[Row]
	}
}

case class AlterViewSetName(
	view: MbTableIdentifier,
	newView: MbTableIdentifier) extends MbRunnableCommand with DDL {
	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		require(view.database == newView.database, s"Rename view cant not rename database")
		val (databaseId, database)= view.database match {
			case Some(db) =>
				val currentDatabase: CatalogDatabase = mbSession.catalog.getDatabase(ctx.organizationId, db)
				(currentDatabase.id.get, currentDatabase.name)
			case None =>
				(ctx.databaseId, ctx.databaseName)
		}
		mbSession.catalog.renameView(databaseId, ctx.organizationName, database, view.table, newView.table, ctx.userId)
		Seq.empty[Row]
	}
}

case class AlterViewSetComment(
	view: MbTableIdentifier,
	comment: String) extends MbRunnableCommand with DDL {
	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val databaseId = view.database match {
			case Some(db) =>
				val currentDatabase: CatalogDatabase = mbSession.catalog.getDatabase(ctx.organizationId, db)
				currentDatabase.id.get
			case None =>
				ctx.databaseId
		}
		val existView: CatalogView = mbSession.catalog.getView(databaseId, view.table)
		mbSession.catalog.alterView(
			existView.copy(
				description = Some(comment),
				updateBy = ctx.userId,
				updateTime = Utils.now
			)
		)
		Seq.empty[Row]
	}
}

case class AlterViewSetQuery(
	view: MbTableIdentifier,
	query: String) extends MbRunnableCommand with DDL {
	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val databaseId = view.database match {
			case Some(db) =>
				val currentDatabase: CatalogDatabase = mbSession.catalog.getDatabase(ctx.organizationId, db)
				currentDatabase.id.get
			case None =>
				ctx.databaseId
		}
		val existView: CatalogView = mbSession.catalog.getView(databaseId, view.table)
		mbSession.catalog.alterView(
			existView.copy(
				cmd = query,
				updateBy = ctx.userId,
				updateTime = Utils.now
			)
		)
		Seq.empty[Row]
	}
}

case class DropView(
	view: MbTableIdentifier,
	ignoreIfNotExists: Boolean) extends MbRunnableCommand with DDL {
	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val (databaseId, database)= view.database match {
			case Some(db) =>
				val currentDatabase: CatalogDatabase = mbSession.catalog.getDatabase(ctx.organizationId, db)
				(currentDatabase.id.get, currentDatabase.name)
			case None =>
				(ctx.databaseId, ctx.databaseName)
		}
		mbSession.catalog.dropView(databaseId, ctx.organizationName, database, view.table, ignoreIfNotExists)
		Seq.empty[Row]
	}
}

case class CreateApplication(
	name: String,
	queryList: Seq[String],
	ignoreIfExists: Boolean) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val catalogApplication = CatalogApplication(
			name = name,
			cmds = queryList,
			organizationId = ctx.organizationId,
			description = None,
			createBy = ctx.userId,
			updateBy = ctx.userId
		)
		mbSession.catalog.createApplication(catalogApplication, ctx.organizationName, ignoreIfExists)
		Seq.empty[Row]
	}
}

case class AlterApplicationSetName(
	name: String,
	newName: String) extends MbRunnableCommand with DDL {

	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		mbSession.catalog.renameApplication(ctx.organizationId, ctx.organizationName, name, newName, ctx.userId)
		Seq.empty[Row]
	}
}

case class AlterApplicationSetQuery(
	name: String,
	queryList: Seq[String]) extends MbRunnableCommand with DDL {
	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		val existApplication: CatalogApplication = mbSession.catalog.getApplication(ctx.userId, name)
		mbSession.catalog.alterApplication(
			existApplication.copy(
				cmds = queryList,
				updateBy = ctx.userId,
				updateTime = Utils.now
			)
		)
		Seq.empty[Row]
	}
}

case class DropApplication(
	name: String,
	ignoreIfNotExists: Boolean) extends MbRunnableCommand with DDL {
	override def run(mbSession: MbSession)(implicit ctx: CatalogSession): Seq[Row] = {
		mbSession.catalog.dropApplication(ctx.organizationId, ctx.organizationName, name, ignoreIfNotExists)
		Seq.empty[Row]
	}
}

case class CreateTempView(
	name: String,
	query: String,
	isCache: Boolean,
	replaceIfExists: Boolean) extends MbCommand with DDL

case class CreateTempFunction(
	name: String,
	props: Map[String, String],
	replaceIfExists: Boolean) extends MbCommand

case class InsertInto(
	table: MbTableIdentifier,
	query: String,
	overwrite: Boolean) extends MbCommand with DDL