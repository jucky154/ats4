package controllers

import javax.inject.Inject
import play.api.Configuration
import play.api.mvc.{Action, InjectedController}

class Index @Inject()(cfg: Configuration) extends InjectedController {
	def view = Action(Ok(views.html.pages.index(cfg)))
}
