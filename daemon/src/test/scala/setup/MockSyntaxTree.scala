package setup

import pragma.domain._

object MockSyntaxTree {
  lazy val code =
    """
    @1 @user
    model Business {
      @1 username: String? @uuid
      @2 email: String @publicCredential @primary
      @3 password: String @secretCredential
      @4 branches: [Branch] 
      @5 mainBranch: Branch? 
      @6 businessType: BusinessType 
    }
    
    @2 model Branch {
      @1 address: String @primary
      @2 business: Business 
    }
    
    enum BusinessType {
      FOOD
      CLOTHING
      OTHER
    }

    config { projectName = "test" }
    """
  lazy val syntaxTree = SyntaxTree.from(code).get
}
