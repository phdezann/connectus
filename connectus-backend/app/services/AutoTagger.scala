package services

import javax.inject.{Inject, Singleton}

@Singleton
class AutoTagger @Inject()(messageService: MessageService, fireBaseFacade: FirebaseFacade) {

  fireBaseFacade.listenContacts(email => messageService.executeTagInbox(email))
}
